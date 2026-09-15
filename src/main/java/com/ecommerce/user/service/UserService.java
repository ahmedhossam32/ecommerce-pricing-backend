package com.ecommerce.service.user;

import com.ecommerce.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    String uploadProfilePicture(MultipartFile file, User user);
}
