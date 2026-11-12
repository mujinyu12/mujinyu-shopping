package com.mujinyu.shopping_file_service.service;

import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.FileService;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@DubboService
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    // 图片大小上限：5MB（与 spring.servlet.multipart.max-file-size 保持一致）
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;

    // 只允许的图片后缀（含点，小写比较）
    private static final Set<String> ALLOWED_SUFFIXES = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final MinioClient minioClient;

    // 存储桶名
    @Value ("${minio.bucket}")
    private String bucket;

    // 对外访问前缀
    @Value ("${minio.fileUrl}")
    private String fileUrl;

    @Override
    public String uploadImage(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusException(CodeEnum.UPLOAD_FILE_ERROR);
        }
        // 大小上限：Controller 已拦一次，Dubbo 直接调用时这里仍然要拦
        if (fileBytes.length > MAX_IMAGE_SIZE) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(), "图片大小不能超过 5MB");
        }
        // 后缀白名单，不接受 .jsp/.html/.svg 等可执行或可注入脚本的类型
        String suffix = getSuffix(fileName).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SUFFIXES.contains(suffix)) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(),
                    "只允许上传 jpg/jpeg/png/gif/webp 格式的图片");
        }
        // 魔数校验：防止把任意文件改名成 .jpg 绕过后缀检查
        if (!isImage(fileBytes)) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(), "文件内容不是合法的图片");
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(fileBytes)) {
            String objectName = UUID.randomUUID().toString().replace("-", "") + suffix;
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName).stream(in, fileBytes.length, -1)
                    .contentType(guessContentType(fileName))
                    .build());
            String url = baseUrl() + "/" + objectName;
            log.info("图片上传成功：{}", url);
            return url;
        } catch (Exception e) {
            log.error("上传文件失败：{}", fileName, e);
            throw new BusException(CodeEnum.UPLOAD_FILE_ERROR);
        }
    }

    @Override
    public void delete(String filePath) {
        // 先校验并还原对象名：非法路径直接抛参数异常，不能进 try 被吞成“删除失败”
        String objectName = toObjectName(filePath);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            log.info("文件删除成功：{}", filePath);
        } catch (Exception e) {
            log.error("文件删除失败：{}", filePath, e);
            throw new BusException(CodeEnum.DELETE_FILE_ERROR);
        }
    }

    /**
     * 启动时自动建桶并设置匿名只读策略。
     * 这样虚拟机里不用手工执行 mc 命令，换一台机器只要改 nacos 的 minio 配置即可。
     * 桶已存在时不会重复创建；策略每次启动都重设一次，避免被人改坏。
     */
    @PostConstruct
    public void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Mino存储桶[{}]创建成功", bucket);
            }
            // 只开放GetObject（读），上传/删除仍然必须带秘钥，前端拿到URL就能直接显示图片
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucket);
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(policy)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("初始化 MinIO 存储桶失败: " + bucket
                    + "，请检查 minio.endpoint / minio.accessKey / minio.secretKey 配置", e);
        }
    }

    /**
     * 把 "http://ip:9100/shopping/abc.jpg" 还原成 "abc.jpg"
     * 数据库里存的是完整 URL，而 MinIO 的 API 只认对象名。
     *
     * <p>安全要求：只允许删除当前桶、当前访问前缀下的对象；
     * 前缀不匹配、包含 ".."、以 "/" 开头或包含反斜杠的路径一律拒绝，
     * 防止通过 filePath 删除其它桶或桶内任意对象。</p>
     */
    private String toObjectName(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        String prefix = baseUrl() + "/";
        if (!filePath.startsWith(prefix)) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(),
                    "只能删除本存储桶内的文件");
        }
        String objectName = filePath.substring(prefix.length());
        if (objectName.isBlank()
                || objectName.startsWith("/")
                || objectName.contains("..")
                || objectName.contains("\\")) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(),
                    "非法的文件路径");
        }
        return objectName;
    }

    /**
     * 对外访问前缀去掉末尾的 "/"，避免配置成 "http://ip:9100/shopping/" 时拼出双斜杠，
     * 导致上传返回的 URL 与删除时校验的前缀不一致
     */
    private String baseUrl() {
        if (fileUrl == null) {
            return "";
        }
        return fileUrl.endsWith("/") ? fileUrl.substring(0, fileUrl.length() - 1) : fileUrl;
    }

    /**
     * 取文件后缀，没有后缀时返回空字符串
     *
     * @param fileName 文件名
     * @return 文件后缀，如 ".jpg"，没有后缀时返回空字符串
     */
    private String getSuffix(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index) : "";
    }

    /**
     * 通过文件头魔数判断是否为允许的图片格式：JPEG、PNG、GIF、WEBP
     */
    private boolean isImage(byte[] b) {
        if (b == null || b.length < 12) {
            return false;
        }
        // JPEG: FF D8 FF
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
        // GIF: "GIF87a" 或 "GIF89a"
        boolean gif = b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
        // WEBP: "RIFF" + 4 字节长度 + "WEBP"
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpeg || png || gif || webp;
    }

    /**
     * 根据文件名猜测 Content-Type，如果猜不出，返回 "application/octet-stream"
     *
     * @param fileName 文件名
     * @return Content-Type
     */
    public String guessContentType(String fileName) {
        String contentType = URLConnection.guessContentTypeFromName(fileName);
        return contentType == null ? "application/octet-stream" : contentType;
    }
}