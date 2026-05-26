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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoService {
    private final VideoRepository videoRepository;
    private final ModelMapper modelMapper;
    private final VideoAnalysisService videoAnalysisService;

    @Value("${videos.storage.base-path}")
    private String basePath;

    //영상 목록 조회
    public Page<VideoResponseDto> getVideos(VideoStatus status,
                                            Long siteId,
                                            Long cameraContext,
                                            String uploadedBy,
                                            ZonedDateTime from,
                                            ZonedDateTime to,
                                            Pageable pageable) {
        Page<Video> videos = videoRepository.findAllByFilter(status, siteId, cameraContext, uploadedBy, from, to, pageable);
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

    //특정 카메라(cameraContext)에 연결된 영상 일괄 삭제 — 카메라 삭제 시 호출
    @Transactional
    public int deleteVideosByCameraContext(Long cameraContext) {
        if (cameraContext == null) return 0;
        List<Video> videos = videoRepository.findAllByCameraContext(cameraContext);
        for (Video v : videos) {
            deleteFileQuietly(v.getFilePath());
            deleteFileQuietly(v.getThumbnailPath());
        }
        videoRepository.deleteAll(videos);
        return videos.size();
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

    //영상 업로드 — 코덱이 H.264 아니면 자동 변환(브라우저 호환 보장)
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

        // 코덱이 H.264 아니면 in-place 로 변환. 실패 시 저장된 파일 정리.
        try {
            ensureH264(storedPath);
        } catch (Exception e) {
            deleteFileQuietly(storedFilename);
            throw new IllegalStateException("영상 처리에 실패했습니다: " + e.getMessage(), e);
        }

        long finalSize;
        try {
            finalSize = Files.size(storedPath);
        } catch (IOException e) {
            finalSize = file.getSize();
        }

        Video video = new Video();
        video.setFilename(originalFilename);
        video.setDisplayName(name != null && !name.isBlank() ? name : originalFilename);
        video.setFilePath(storedFilename);
        video.setFileSize(finalSize);
        //TODO: FFprobe 연동하여 duration/resolution/fps 추출
        video.setStatus(VideoStatus.UPLOADED);
        video.setSiteId(siteId);
        video.setCameraContext(cameraContext);
        video.setDescription(description);
        video.setUploadedBy(uploadedBy);

        Video saved = videoRepository.save(video);

        if (cameraContext != null) {
            videoAnalysisService.startAnalysis(saved.getVideoId(), null);
        }

        return convertToDto(saved);
    }

    // ffprobe 로 첫 번째 비디오 스트림 코덱명 추출 (예: h264, hevc, mpeg4)
    private String probeVideoCodec(Path videoPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_name",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoPath.toString()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output;
        try (java.io.InputStream in = proc.getInputStream()) {
            output = new String(in.readAllBytes()).trim();
        }
        if (!proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IOException("ffprobe 시간 초과");
        }
        return output;
    }

    // 코덱이 h264 가 아니면 ffmpeg 로 in-place 재인코딩 (libx264 + faststart)
    private void ensureH264(Path videoPath) throws IOException, InterruptedException {
        String codec = probeVideoCodec(videoPath);
        if ("h264".equalsIgnoreCase(codec)) {
            return; // 이미 호환 코덱
        }

        Path tmp = videoPath.resolveSibling(videoPath.getFileName().toString() + ".tmp.mp4");
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", videoPath.toString(),
                "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                "-c:a", "aac",
                "-movflags", "+faststart",
                tmp.toString()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        // 큰 영상도 변환 가능하도록 충분히 길게(10분), 진행 로그는 버림
        try (java.io.InputStream in = proc.getInputStream()) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        if (!proc.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) {
            proc.destroyForcibly();
            Files.deleteIfExists(tmp);
            throw new IOException("ffmpeg 변환 시간 초과");
        }
        if (proc.exitValue() != 0) {
            Files.deleteIfExists(tmp);
            throw new IOException("ffmpeg 변환 실패 (exit=" + proc.exitValue() + ", source codec=" + codec + ")");
        }

        Files.move(tmp, videoPath, StandardCopyOption.REPLACE_EXISTING);
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
            // Spring MultipartFile.transferTo(File) 는 상대 경로 해석이 구현/환경별로 갈리므로
            // 절대 경로 + Files.copy(InputStream) 로 안정화.
            Path baseDir = Paths.get(basePath).toAbsolutePath().normalize();
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(storedFilename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("영상 파일 저장에 실패했습니다: " + e.getMessage(), e);
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
