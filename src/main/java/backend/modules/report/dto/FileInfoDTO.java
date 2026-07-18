package backend.modules.report.dto;

/**
 * Standardized DTO containing raw source code for a single file.
 *
 * @param name         original file name
 * @param rawCode      the raw un-normalized source code content
 */
public record FileInfoDTO(String name, String rawCode) {}
