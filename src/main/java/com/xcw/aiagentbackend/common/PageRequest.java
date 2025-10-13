package com.xcw.aiagentbackend.common;


import lombok.Data;

/**
 * @author 2340129326 许灿炜
 * @date 2025/10/13
 */

/**
 * 分页请求
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";
}
