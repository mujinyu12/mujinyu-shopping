package com.mujinyu.shopping_manager_api.controller;

import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.FileService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/file")
public class FileController {

    // 与服务端保持一致：5MB
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;

    // 图片后缀白名单
    private static final Set<String> ALLOWED_SUFFIXES = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @DubboReference
    private FileService fileService;

    /**
     * 上传文件
     * @param file 文件
     * @return 文件路径
     */
    @PostMapping("/uploadImage")
    public BaseResult<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusException(CodeEnum.UPLOAD_FILE_ERROR);
        }
        // 先做大小和后缀校验，避免把大文件/非图片传到 Dubbo 层再失败
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(), "上传图片不能超过 5MB");
        }
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null) {
            int index = originalFilename.lastIndexOf('.');
            if (index >= 0) {
                suffix = originalFilename.substring(index + 1).toLowerCase(Locale.ROOT);
            }
        }
        if (!ALLOWED_SUFFIXES.contains(suffix)) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(),
                    "只允许上传 jpg/jpeg/png/gif/webp 格式的图片");
        }
        // MultipartFile对象不能在服务间传递，必须转为byte数组
        try {
            byte[] bytes = file.getBytes();
            return BaseResult.ok(fileService.uploadImage(bytes, originalFilename));
        } catch (IOException e) {
            throw new BusException(CodeEnum.UPLOAD_FILE_ERROR.getCode(), "读取上传文件失败");
        }
    }

    /**
     * 删除文件
     * @param filePath 文件路径
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    public BaseResult delete(@RequestParam("filePath") String filePath) {
        fileService.delete(filePath);
        return BaseResult.ok();
    }
}