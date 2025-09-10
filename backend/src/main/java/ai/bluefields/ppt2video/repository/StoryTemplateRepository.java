package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.StoryTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StoryTemplateRepository extends JpaRepository<StoryTemplate, UUID> {

  Optional<StoryTemplate> findByName(String name);

  List<StoryTemplate> findByIsActiveTrue();

  List<StoryTemplate> findByCategory(String category);

  @Query(
      "SELECT st FROM StoryTemplate st WHERE st.isActive = true "
          + "AND (:category IS NULL OR st.category = :category) "
          + "ORDER BY st.name")
  List<StoryTemplate> findActiveTemplatesByCategory(@Param("category") String category);

  boolean existsByName(String name);

  @Query("SELECT DISTINCT st.category FROM StoryTemplate st WHERE st.category IS NOT NULL")
  List<String> findDistinctCategories();
}
