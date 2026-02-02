package com.g4stly.templateApp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response for User Activity Logs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLogListResponse {
    private List<UserActivityLogDTO> logs;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
}
