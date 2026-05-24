package com.ecommerce.service.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public String uploadProfilePicture(MultipartFile file, Long userId) {
        try {
            Map result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", "profile_pictures",
                "public_id", "user_" + userId,
                "overwrite", true,
                "resource_type", "image",
                "transformation", ObjectUtils.asMap(
                    "width", 300, "height", 300, "crop", "fill", "gravity", "face"
                )
            ));
            return result.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Profile picture upload failed: " + e.getMessage());
        }
    }

    public String uploadProductImage(MultipartFile file, Long productId, int index) {
        try {
            Map result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", "product_images",
                "public_id", "product_" + productId + "_img_" + index,
                "overwrite", true,
                "resource_type", "image"
            ));
            return result.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Product image upload failed: " + e.getMessage());
        }
    }

    public void deleteImage(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new RuntimeException("Image deletion failed: " + e.getMessage());
        }
    }
}
