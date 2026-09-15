package com.ecommerce.user.service;
import com.ecommerce.user.entity.User;

import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    String uploadProfilePicture(MultipartFile file, User user);
}
