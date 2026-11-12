package com.mujinyu.shopping_common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {
    private long total;  // 总记录数
    private int page;    // 当前页码
    private int size;    // 每页大小
    private List<T> list; // 当前页数据列表
}
