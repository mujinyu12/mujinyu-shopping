package com.mujinyu.shopping_common.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 后台管理员用户
 */
@Data
public class Admin implements Serializable {
    @TableId
    private Long aid; // 用户id
    private String username; // 用户名
    // 密码只允许反序列化（新增/修改入参），序列化返回前端时一律忽略，避免哈希泄露
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password; // 密码
    @TableField(exist = false) // 数据库不存在该字段
    private List<Role> roles; // 用户拥有的角色集合
}