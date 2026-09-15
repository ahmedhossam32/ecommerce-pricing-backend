package com.ecommerce.service.user;

import com.ecommerce.entity.User;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.upload.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final CloudinaryService cloudinaryService;
    private final UserRepository userRepository;

    @Override
    public String uploadProfilePicture(MultipartFile file, User user) {
        String url = cloudinaryService.uploadProfilePicture(file, user.getId());
        user.setProfilePictureUrl(url);
        userRepository.save(user);
        return url;
    }
}
