package ai.bluefields.ppt2video.entity;

/**
 * Enumeration representing different types of slides in a presentation. Used to determine
 * appropriate narrative lengths and styles for different slide categories.
 */
public enum SlideType {
  /** Introduction/title slide - typically the first slide */
  INTRO,

  /** Regular content slide with substantive information */
  CONTENT,

  /** Section separator/divider slide between major sections */
  SEPARATOR,

  /** Thank you/closing slide - typically the last slide */
  THANK_YOU,

  /** Unknown or unclassified slide type */
  UNKNOWN
}
