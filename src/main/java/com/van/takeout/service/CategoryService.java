package com.van.takeout.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.van.takeout.entity.Category;

public interface CategoryService extends IService<Category> {
    void remove(Long id);
}
