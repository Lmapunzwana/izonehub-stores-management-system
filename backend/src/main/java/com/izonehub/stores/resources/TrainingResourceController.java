package com.izonehub.stores.resources;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

/**
 * Serves the onboarding training-video package. This is deliberately NOT an
 * email attachment — a 64MB zip is well past what Resend (or most providers)
 * will attach — so the welcome email links here instead, and the user
 * downloads it once logged in.
 *
 * No @PreAuthorize: any authenticated user (any role) can download it, same
 * as the rest of the app's "authenticated by default" posture in
 * SecurityConfig — there's nothing role-specific about training material.
 *
 * Supports HTTP Range requests (206 Partial Content) so a dropped connection
 * on a slow/unreliable link can resume instead of restarting a 64MB download
 * from zero.
 */
@RestController
@RequestMapping("/api/resources")
public class TrainingResourceController {

    private static final String FILENAME = "NSV_Stores_Training_Videos.zip";

    @GetMapping("/training-videos")
    public ResponseEntity<?> downloadTrainingVideos(@org.springframework.web.bind.annotation.RequestHeader(value = "Range", required = false) String rangeHeader) {
        ClassPathResource resource = new ClassPathResource(FILENAME);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Training video package is not available.");
        }

        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read training video package.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + FILENAME + "\"");
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (rangeHeader == null) {
            headers.setContentLength(contentLength);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(resource);
        }

        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        HttpRange range = ranges.get(0);
        ResourceRegion region = range.toResourceRegion(resource);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(region);
    }
}
