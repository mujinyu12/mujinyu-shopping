package com.mujinyu.shopping_user_service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mujinyu.shopping_common.pojo.ShoppingUser;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.ShoppingUserService;
import com.mujinyu.shopping_common.util.PasswordUtil;
import com.mujinyu.shopping_common.util.TokenUtils;
import com.mujinyu.shopping_user_service.mapper.ShoppingUserMapper;
import com.mujinyu.shopping_user_service.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@DubboService
@RequiredArgsConstructor
public class ShoppingUserServiceImpl implements ShoppingUserService {

    private static final int MAX_CHECK_ATTEMPTS = 5; // 验证码最大失败次数
    private static final long CODE_LOCK_SECONDS = 15 * 60L; // 连续失败达到上限后的锁定时长（15 分钟）
    private static final String SCENE_REGISTER = "register"; // 注册验证码场景
    private static final String SCENE_LOGIN = "login"; // 登录验证码场景

    private final RedisTemplate redisTemplate;
    private final ShoppingUserMapper shoppingUserMapper;


    @Override
    public void saveRegisterCheckCode(String phone, String checkCode) {
        ValueOperations valueOperations = redisTemplate.opsForValue();
        // redis 中保存的键为 registerCode:手机号，值为验证码，过期时间 5 分钟
        valueOperations.set("registerCode:" + phone, checkCode, 300, TimeUnit.SECONDS);
    }


    @Override
    public void registerCheckCode(String phone, String checkCode) {
        // 构建存储验证码的Redis键
        String codeKey = "registerCode:" + phone;
        // 锁定期间直接拒绝，避免绕过计数继续撞码
        assertCheckCodeUnlocked(SCENE_REGISTER, phone, CodeEnum.REGISTER_CODE_ERROR);
        // 从Redis获取存储的验证码（只读校验，真正的消费在 register 的 GETDEL）
        Object checkCodeRedis = redisTemplate.opsForValue().get(codeKey);
        if (checkCodeRedis == null || !checkCode.equals(checkCodeRedis.toString())) {
            // 校验失败：累计失败次数，达到上限锁定 15 分钟
            recordCheckCodeFailure(SCENE_REGISTER, phone, codeKey);
            throw new BusException(CodeEnum.REGISTER_CODE_ERROR);
        }
        // 校验成功：清除失败计数
        clearCheckCodeFailure(SCENE_REGISTER, phone);
    }

    @Override
    public void register(ShoppingUser shoppingUser, String checkCode) {
        String phone = shoppingUser.getPhone(); // 获取用户手机号
        String codeKey = "registerCode:" + phone; // 注册验证码在 Redis 中的键
        // 锁定期间直接拒绝，连正确验证码也不再放行
        assertCheckCodeUnlocked(SCENE_REGISTER, phone, CodeEnum.REGISTER_CODE_ERROR);
        // 0、比对并消费验证码：先比对再删除，只有校验通过才删除，避免“输错一次就把正确验证码删掉”
        if (!consumeCheckCode(codeKey, checkCode)) {
            // 校验失败：累计失败次数，达到上限锁定 15 分钟
            recordCheckCodeFailure(SCENE_REGISTER, phone, codeKey);
            throw new BusException(CodeEnum.REGISTER_CODE_ERROR);
        }
        // 验证码校验通过，清除失败计数
        clearCheckCodeFailure(SCENE_REGISTER, phone);
        // 1、验证手机号是否已注册
        QueryWrapper<ShoppingUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phone);
        List<ShoppingUser> shoppingUsers = shoppingUserMapper.selectList(queryWrapper);
        if (shoppingUsers != null && !shoppingUsers.isEmpty()) {
            throw new BusException(CodeEnum.REGISTER_REPEAT_PHONE_ERROR);
        }
        // 2、验证用户名是否已存在
        String username = shoppingUser.getUsername();
        QueryWrapper<ShoppingUser> queryWrapper1 = new QueryWrapper<>();
        queryWrapper1.eq("username", username);
        List<ShoppingUser> shoppingUsers1 = shoppingUserMapper.selectList(queryWrapper1);
        if (shoppingUsers1 != null && !shoppingUsers1.isEmpty()) {
            throw new BusException(CodeEnum.REGISTER_REPEAT_NAME_ERROR);
        }
        // 3、新增用户：密码必须加密后再入库
        shoppingUser.setId(null);  // 防止前端传入 id，导致主键冲突
        shoppingUser.setStatus("Y");
        shoppingUser.setPassword(PasswordUtil.encode(shoppingUser.getPassword()));
        shoppingUserMapper.insert(shoppingUser);
    }

    @Override
    public String loginPassword(String username, String password) {
        // 1、验证用户名和密码
        QueryWrapper<ShoppingUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        ShoppingUser shoppingUser = shoppingUserMapper.selectOne(queryWrapper);
        if (shoppingUser == null || !PasswordUtil.matches(password, shoppingUser.getPassword())) {
            throw new BusException(CodeEnum.LOGIN_NAME_PASSWORD_ERROR);
        }
        // 2、验证用户状态：封禁用户不允许密码登录
        assertUserAvailable(shoppingUser);
        // 旧哈希在登录成功后透明升级为BCrypt
        if (PasswordUtil.needsUpgrade(shoppingUser.getPassword())) {
            ShoppingUser upgrade = new ShoppingUser();
            upgrade.setId(shoppingUser.getId());
            upgrade.setPassword(PasswordUtil.encode(password));
            shoppingUserMapper.updateById(upgrade);
        }
        // 3、生成 JWT 令牌返回
        return JwtUtils.sign(shoppingUser.getId(), shoppingUser.getUsername());
    }

    @Override
    public void saveLoginCheckCode(String phone, String checkCode) {
        ValueOperations valueOperations = redisTemplate.opsForValue();
        // redis 的键为 loginCode:手机号，值为验证码，过期时间 5 分钟
        valueOperations.set("loginCode:" + phone, checkCode, 300, TimeUnit.SECONDS);
    }

    @Override
    public String loginCheckCode(String phone, String checkCode) {
        // 1、验证手机号验证码是否在 redis 中存在
        String codeKey = "loginCode:" + phone; // 构建验证码在Redis中的存储键
        // 锁定期间直接拒绝，避免绕过计数继续撞码
        assertCheckCodeUnlocked(SCENE_LOGIN, phone, CodeEnum.LOGIN_CODE_ERROR);
        // 原子比对并删除：一次有效，且只有校验通过才删除（输错不会销毁正确验证码）
        if (!consumeCheckCode(codeKey, checkCode)) {
            // 校验失败：累计失败次数，达到上限锁定 15 分钟
            recordCheckCodeFailure(SCENE_LOGIN, phone, codeKey);
            throw new BusException(CodeEnum.LOGIN_CODE_ERROR);
        }
        // 校验通过，清除失败计数
        clearCheckCodeFailure(SCENE_LOGIN, phone);
        // 2、登录成功，根据手机号查询用户
        QueryWrapper<ShoppingUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phone);
        ShoppingUser shoppingUser = shoppingUserMapper.selectOne(queryWrapper);
        if (shoppingUser == null) {
            throw new BusException(CodeEnum.LOGIN_NOPHONE_ERROR);
        }
        // 封禁用户不允许登录（与密码登录、发码链路共用同一套状态校验）
        assertUserAvailable(shoppingUser);
        // 3、生成 JWT 令牌返回
        return JwtUtils.sign(shoppingUser.getId(), shoppingUser.getUsername());
    }

    /**
     * 验证码失败计数：同一手机号在同一场景下累计失败，达到上限后锁定 15 分钟
     * 计数键：codeFail:{scene}:{phone}，锁定标记键：codeLock:{scene}:{phone}
     *
     * @param scene   场景（register / login）
     * @param phone   手机号
     * @param codeKey 验证码在 Redis 中的键，锁定时一并删除，保证正确验证码也不再放行
     */
    private void recordCheckCodeFailure(String scene, String phone, String codeKey) {
        String failKey = "codeFail:" + scene + ":" + phone;
        String lockKey = "codeLock:" + scene + ":" + phone;
        Long fails = redisTemplate.opsForValue().increment(failKey);
        if (fails == null) {
            return;
        }
        // 首次失败时给计数键设置过期时间，避免无效手机号长期占用 Redis
        if (fails == 1L) {
            redisTemplate.expire(failKey, CODE_LOCK_SECONDS, TimeUnit.SECONDS);
        }
        if (fails >= MAX_CHECK_ATTEMPTS) {
            // 达到上限：删除验证码并打上锁定标记，锁定期间即使验证码正确也拒绝
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failKey);
            redisTemplate.opsForValue().set(lockKey, "1", CODE_LOCK_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 是否处于验证码锁定状态；锁定期间直接抛对应场景的验证码错误
     */
    private void assertCheckCodeUnlocked(String scene, String phone, CodeEnum errorCode) {
        String lockKey = "codeLock:" + scene + ":" + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BusException(errorCode);
        }
    }

    /**
     * 原子地比对并消费验证码：只有比对通过才删除，避免“输错一次把正确验证码删掉”。
     * <p>
     * 用 WATCH + MULTI/EXEC 实现乐观锁：并发下只有一个请求能拿到并删掉验证码，其余请求 EXEC 返回空集合视为失败。
     *
     * @param codeKey 验证码在 Redis 中的键
     * @param input   用户提交的验证码
     * @return true=比对通过且已删除（本次消费成功）；false=验证码不存在、已过期或与提交值不一致（不删除）
     */
    private boolean consumeCheckCode(String codeKey, String input) {
        Boolean consumed = (Boolean) redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) {
                // 1、监视该键，比对期间若被其它请求修改/删除，事务会被放弃
                operations.watch(codeKey);
                Object cached = operations.opsForValue().get(codeKey);
                // 2、验证码不存在或与用户提交值不一致：放弃事务，且不删除（关键点）
                if (cached == null || input == null || !cached.toString().equals(input)) {
                    operations.unwatch();
                    return Boolean.FALSE;
                }
                // 3、比对通过：在一个事务里删除验证码，保证“一次有效”
                operations.multi();
                operations.delete(codeKey);
                return !operations.exec().isEmpty();
            }
        });
        return Boolean.TRUE.equals(consumed);
    }

    /**
     * 校验成功后清除失败计数（锁定标记不会在成功时存在，无需处理）
     */
    private void clearCheckCodeFailure(String scene, String phone) {
        redisTemplate.delete("codeFail:" + scene + ":" + phone);
    }

    @Override
    public String getName(String token) {
        Map<String, Object> verify = JwtUtils.verify(token);
        return (String) verify.get("username");
    }

    @Override
    public ShoppingUser getLoginUser(String token) {
        // 令牌非法/过期时 TokenUtils.getUserId 会直接抛 NOT_LOGIN
        Long userId = TokenUtils.getUserId(token);
        if (userId == null) {
            throw new BusException(CodeEnum.NOT_LOGIN);
        }
        // 根据 id 查询用户
        return shoppingUserMapper.selectById(userId);
    }

    @Override
    public void checkPhone(String phone) {
        // 1、判断手机号是否存在
        QueryWrapper<ShoppingUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phone);
        ShoppingUser shoppingUser = shoppingUserMapper.selectOne(queryWrapper);
        if (shoppingUser == null) {
            throw new BusException(CodeEnum.LOGIN_NOPHONE_ERROR);
        }
        // 2、判断用户状态是否正常
        assertUserAvailable(shoppingUser);
    }

    /**
     * 用户状态校验：封禁用户不能登录（密码登录、验证码登录、发送登录验证码三条链路共用）
     */
    private void assertUserAvailable(ShoppingUser user) {
        if (!"Y".equals(user.getStatus())) {
            throw new BusException(CodeEnum.LOGIN_USER_STATUS_ERROR);
        }
    }
}
