package ai.bluefields.ppt2video.service.ai.narrative;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to detect redundant transition phrases that repeat the opening of the next slide.
 * Prevents awkward repetition in the narrative flow.
 */
@Slf4j
@Service
public class TransitionRedundancyChecker {

  // Common connecting words that don't count as meaningful overlap
  private static final Set<String> STOP_WORDS =
      new HashSet<>(
          Arrays.asList(
              "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with",
              "by", "from", "about", "as", "into", "through", "during", "before", "after", "above",
              "below", "between", "under", "let's", "we'll", "here's", "now", "next", "then",
              "first", "second", "third", "finally"));

  /**
   * Checks if a transition phrase is redundant with the opening of the next narrative.
   *
   * @param transitionPhrase The transition phrase from the current slide
   * @param nextNarrativeText The full narrative text of the next slide
   * @return true if the transition is redundant and should be skipped
   */
  public boolean isTransitionRedundant(String transitionPhrase, String nextNarrativeText) {
    if (transitionPhrase == null
        || transitionPhrase.trim().isEmpty()
        || nextNarrativeText == null
        || nextNarrativeText.trim().isEmpty()) {
      return false;
    }

    // Get the first sentence or first 200 characters of the next narrative
    String nextOpening = extractOpening(nextNarrativeText);

    // Extract key concepts from both texts
    Set<String> transitionConcepts = extractKeyConcepts(transitionPhrase.toLowerCase());
    Set<String> nextOpeningConcepts = extractKeyConcepts(nextOpening.toLowerCase());

    // Calculate overlap
    Set<String> overlap = new HashSet<>(transitionConcepts);
    overlap.retainAll(nextOpeningConcepts);

    // If more than 40% of transition concepts appear in the opening, it's redundant
    double overlapRatio =
        transitionConcepts.isEmpty() ? 0 : (double) overlap.size() / transitionConcepts.size();

    boolean isRedundant = overlapRatio > 0.4;

    if (isRedundant) {
      log.info("Redundant transition detected. Overlap ratio: {}", overlapRatio);
      log.debug("Transition concepts: {}", transitionConcepts);
      log.debug("Next opening concepts: {}", nextOpeningConcepts);
      log.debug("Overlapping concepts: {}", overlap);
    }

    return isRedundant;
  }

  /** Extracts the opening portion of a narrative (first sentence or 200 chars). */
  private String extractOpening(String text) {
    // Find first sentence ending
    int sentenceEnd = -1;
    for (String delimiter : new String[] {".", "!", "?"}) {
      int index = text.indexOf(delimiter);
      if (index > 0 && (sentenceEnd == -1 || index < sentenceEnd)) {
        sentenceEnd = index;
      }
    }

    // Use first sentence if found and reasonable length, otherwise first 200 chars
    if (sentenceEnd > 20 && sentenceEnd < 250) {
      return text.substring(0, sentenceEnd);
    } else {
      return text.length() > 200 ? text.substring(0, 200) : text;
    }
  }

  /** Extracts meaningful concepts from text, filtering out stop words. */
  private Set<String> extractKeyConcepts(String text) {
    Set<String> concepts = new HashSet<>();

    // Remove punctuation and split into words
    String cleanText = text.replaceAll("[^a-z0-9\\s-]", " ");
    String[] words = cleanText.split("\\s+");

    // Look for meaningful phrases and words
    for (int i = 0; i < words.length; i++) {
      String word = words[i].trim();

      // Skip stop words and very short words
      if (word.length() < 3 || STOP_WORDS.contains(word)) {
        continue;
      }

      // Add single meaningful word
      concepts.add(word);

      // Look for two-word phrases
      if (i < words.length - 1) {
        String nextWord = words[i + 1].trim();
        if (nextWord.length() >= 3 && !STOP_WORDS.contains(nextWord)) {
          String phrase = word + " " + nextWord;

          // Add meaningful two-word phrases
          if (isMeaningfulPhrase(phrase)) {
            concepts.add(phrase);
          }
        }
      }
    }

    return concepts;
  }

  /** Determines if a two-word phrase is meaningful (domain-specific terms). */
  private boolean isMeaningfulPhrase(String phrase) {
    // Key business/presentation phrases to recognize
    String[] meaningfulPhrases = {
      "inflection point",
      "focus areas",
      "three areas",
      "four steps",
      "engagement model",
      "workshop phase",
      "workstreams",
      "investment view",
      "technical setup",
      "team scaling",
      "product development",
      "strategic guidance",
      "technology review",
      "data collection"
    };

    for (String meaningful : meaningfulPhrases) {
      if (phrase.contains(meaningful)) {
        return true;
      }
    }

    return false;
  }
}
