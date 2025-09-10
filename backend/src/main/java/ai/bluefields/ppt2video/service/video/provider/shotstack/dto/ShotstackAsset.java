package ai.bluefields.ppt2video.service.video.provider.shotstack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Base class for Shotstack assets */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ShotstackVideoAsset.class, name = "video"),
  @JsonSubTypes.Type(value = ShotstackImageAsset.class, name = "image"),
  @JsonSubTypes.Type(value = ShotstackHtmlAsset.class, name = "html"),
  @JsonSubTypes.Type(value = ShotstackLumaAsset.class, name = "luma")
})
public abstract class ShotstackAsset {
  private String type;
}
