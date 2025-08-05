package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.SlideImage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository interface for SlideImage entity operations. */
@Repository
public interface SlideImageRepository extends JpaRepository<SlideImage, UUID> {}
