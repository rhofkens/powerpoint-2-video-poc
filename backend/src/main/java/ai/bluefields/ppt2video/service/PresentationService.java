package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for managing presentations. Handles business logic for presentation operations
 * including CRUD operations and coordination of the presentation processing pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PresentationService {

  private final PresentationRepository presentationRepository;

  /**
   * Retrieves all presentations from the database.
   *
   * @return list of all presentations
   */
  public List<Presentation> findAll() {
    return presentationRepository.findAll();
  }

  /**
   * Finds a presentation by its unique identifier.
   *
   * @param id the UUID of the presentation
   * @return Optional containing the presentation if found, empty otherwise
   */
  public Optional<Presentation> findById(UUID id) {
    return presentationRepository.findById(id);
  }

  /**
   * Saves or updates a presentation in the database.
   *
   * @param presentation the presentation entity to save
   * @return the saved presentation with generated ID if new
   */
  public Presentation save(Presentation presentation) {
    log.info("Saving presentation: {}", presentation.getTitle());
    return presentationRepository.save(presentation);
  }

  /**
   * Deletes a presentation by its unique identifier. This will cascade delete all associated
   * slides.
   *
   * @param id the UUID of the presentation to delete
   */
  public void deleteById(UUID id) {
    log.info("Deleting presentation with id: {}", id);
    presentationRepository.deleteById(id);
  }
}
