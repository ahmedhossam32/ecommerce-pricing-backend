package com.ecommerce.common.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    private final Cloudinary cloudinary;

    private void validateFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum size of 5MB");
        }
    }

    public String uploadProfilePicture(MultipartFile file, Long userId) {
        validateFile(file);
        try {
            Map result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "profile_pictures",
                    "public_id", "user_" + userId,
                    "overwrite", true,
                    "resource_type", "image",
                    "transformation", new com.cloudinary.Transformation()
                            .width(300).height(300).crop("fill").gravity("face")
            ));
            return result.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Profile picture upload failed: " + e.getMessage());
        }
    }

    public String uploadProductImage(MultipartFile file, Long productId, int index) {
        validateFile(file);
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
}
