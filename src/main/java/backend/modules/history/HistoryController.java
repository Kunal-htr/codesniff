package backend.modules.history;

import backend.common.exception.HistoryNotFoundException;
import backend.common.exception.UnauthorizedException;
import backend.modules.history.dto.HistoryListItemDTO;
import backend.modules.history.dto.PinRequestDTO;
import backend.modules.user.User;
import backend.modules.user.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final AnalysisHistoryRepository historyRepository;
    private final UserRepository userRepository;

    public HistoryController(AnalysisHistoryRepository historyRepository, UserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Page<HistoryListItemDTO> getHistory(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
            
        if (principal == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Pinned items sort first always
        Sort sort = Sort.by(Sort.Direction.DESC, "isPinned").and(Sort.by(direction, sortBy));
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<AnalysisHistory> results;
        if (search == null || search.trim().isEmpty()) {
            results = historyRepository.findByUserId(user.getId(), pageable);
        } else {
            results = historyRepository.searchHistory(user.getId(), search.trim(), pageable);
        }
        
        return results.map(h -> new HistoryListItemDTO(
                h.getId(),
                h.getBatchId(),
                h.getFileNames(),
                h.getTotalPairs(),
                h.getHighestSimilarity(),
                h.getAverageSimilarity(),
                h.isPinned(),
                h.getCreatedAt()
        ));
    }

    @GetMapping("/{id}")
    public AnalysisHistory getHistoryEntry(@PathVariable UUID id, Principal principal) {
        return getOwnedHistory(id, principal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHistoryEntry(@PathVariable UUID id, Principal principal) {
        AnalysisHistory history = getOwnedHistory(id, principal);
        historyRepository.delete(history);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/pin")
    public AnalysisHistory togglePin(@PathVariable UUID id, @RequestBody PinRequestDTO request, Principal principal) {
        AnalysisHistory history = getOwnedHistory(id, principal);
        history.setPinned(request.pinned());
        return historyRepository.save(history);
    }

    /**
     * Checks if the requested history exists and belongs to the authenticated user.
     * Returns 404 for BOTH missing ID and wrong user to prevent ID enumeration.
     */
    private AnalysisHistory getOwnedHistory(UUID historyId, Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        AnalysisHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new HistoryNotFoundException(historyId.toString()));
                
        if (!history.getUserId().equals(user.getId())) {
            throw new HistoryNotFoundException(historyId.toString());
        }
        
        return history;
    }
}
