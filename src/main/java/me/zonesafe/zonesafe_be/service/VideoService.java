package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Video;
import me.zonesafe.zonesafe_be.dto.VideoResponseDto;
import me.zonesafe.zonesafe_be.enums.VideoStatus;
import me.zonesafe.zonesafe_be.repository.VideoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoService {
    private final VideoRepository videoRepository;
    private final ModelMapper modelMapper;

    @Value("${videos.storage.base-path}")
    private String basePath;

    //영상 목록 조회
    public Page<VideoResponseDto> getVideos(VideoStatus status,
                                            Long siteId,
                                            String uploadedBy,
                                            ZonedDateTime from,
                                            ZonedDateTime to,
                                            Pageable pageable) {
        Page<Video> videos = videoRepository.findAllByFilter(status, siteId, uploadedBy, from, to, pageable);
        return videos.map(this::convertToDto);
    }

    //영상 상세 조회
    public VideoResponseDto getVideoById(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 영상이 존재하지 않습니다. ID: " + videoId));
        return convertToDto(video);
    }

    //영상 파일 리소스 조회 (스트리밍/다운로드용)
    public Resource loadVideoResource(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 영상이 존재하지 않습니다. ID: " + videoId));

        Path path = resolvePath(video.getFilePath());
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("영상 파일이 존재하지 않습니다. ID: " + videoId);
        }
        return resource;
    }

    //영상 썸네일 리소스 조회
    public Resource loadThumbnailResource(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 영상이 존재하지 않습니다. ID: " + videoId));

        if (video.getThumbnailPath() == null || video.getThumbnailPath().isBlank()) {
            throw new IllegalArgumentException("해당 영상의 썸네일이 없습니다. ID: " + videoId);
        }

        Path path = resolvePath(video.getThumbnailPath());
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("썸네일 파일이 존재하지 않습니다. ID: " + videoId);
        }
        return resource;
    }

    //영상 삭제
    @Transactional
    public void deleteVideo(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("삭제하려는 영상이 존재하지 않습니다. ID: " + videoId));

        //TODO: 영상 분석으로 생성된 클립(generatedClipIds)도 함께 삭제
        deleteFileQuietly(video.getFilePath());
        deleteFileQuietly(video.getThumbnailPath());

        videoRepository.delete(video);
    }

    private void deleteFileQuietly(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return;
        try {
            Files.deleteIfExists(resolvePath(storedPath));
        } catch (IOException ignored) {
        }
    }

    private Path resolvePath(String storedPath) {
        Path stored = Paths.get(storedPath);
        return stored.isAbsolute() ? stored : Paths.get(basePath).resolve(storedPath).normalize();
    }

    //영상 업로드
    @Transactional
    public VideoResponseDto uploadVideo(MultipartFile file,
                                        String name,
                                        Long siteId,
                                        Long cameraContext,
                                        String description,
                                        String uploadedBy) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "video.mp4";
        String storedFilename = UUID.randomUUID() + ".mp4";
        Path storedPath = saveToStorage(file, storedFilename);

        Video video = new Video();
        video.setFilename(originalFilename);
        video.setDisplayName(name != null && !name.isBlank() ? name : originalFilename);
        video.setFilePath(storedFilename);
        video.setFileSize(file.getSize());
        //TODO: FFprobe 연동하여 duration/resolution/fps/codec 추출
        video.setStatus(VideoStatus.UPLOADED);
        video.setSiteId(siteId);
        video.setCameraContext(cameraContext);
        video.setDescription(description);
        video.setUploadedBy(uploadedBy);

        Video saved = videoRepository.save(video);
        return convertToDto(saved);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 비어 있습니다.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".mp4")) {
            throw new IllegalArgumentException("MP4 파일만 업로드 가능합니다.");
        }
        //TODO: FFprobe 로 코덱(H.264/H.265) 검증
    }

    private Path saveToStorage(MultipartFile file, String storedFilename) {
        try {
            Path baseDir = Paths.get(basePath);
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(storedFilename).normalize();
            file.transferTo(target.toFile());
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("영상 파일 저장에 실패했습니다.", e);
        }
    }

    VideoResponseDto convertToDto(Video video) {
        VideoResponseDto dto = modelMapper.map(video, VideoResponseDto.class);
        dto.setDownloadUrl("/api/v1/videos/" + video.getVideoId() + "/download");
        dto.setStreamUrl("/api/v1/videos/" + video.getVideoId() + "/stream");
        dto.setThumbnailUrl("/api/v1/videos/" + video.getVideoId() + "/thumbnail");
        return dto;
    }
}
