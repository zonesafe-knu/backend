package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.VideoResponseDto;
import me.zonesafe.zonesafe_be.service.VideoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {
    private final VideoService videoService;

    //영상 업로드 (TODO: Spring Security 도입 후 @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") 적용)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> uploadVideo(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long cameraContext,
            @RequestParam(required = false) String description
    ) {
        //TODO: 인증 도입 후 SecurityContext 에서 username 추출
        String uploadedBy = "admin";
        VideoResponseDto dto = videoService.uploadVideo(file, name, siteId, cameraContext, description, uploadedBy);
        return Map.of("success", true, "data", dto);
    }
}
