# Plan: Improve Deck Analysis to Extract Title and Author

## Problem Statement

Currently, the video composition service hardcodes:
- Title: "Consulting Proposal" 
- Subtitle: "By Roeland Hofkens"

The actual title from the database is a filename like "05052025-tembi-consulting-proposal" which isn't suitable for display. We need to extract the actual presentation title (from the first slide) and author/company information through the AI deck analysis.

## Solution Overview

Enhance the DeckAnalysisService to extract presentation title and author/company information by:
1. Improving the AI prompt to specifically request title and author extraction
2. Adding new fields to store this information in the DeckAnalysis entity
3. Updating the video composition to use these extracted values

## Current State Analysis

### DeckAnalysisService
- Currently extracts: overallStory, communicationIntent, keyThemes, targetAudience, tone
- Passes slide content including title and contentText to the AI
- Uses GPT-4 for analysis

### Data Available to AI
The AI receives for each slide:
- `slideNumber`
- `title` (from slide.getTitle())
- `contentText` (all text content)
- `speakerNotes`

The first slide typically contains the presentation title in large font and often the author/company name.

## Implementation Plan

### Phase 1: Database Schema Update (30 minutes)

Add new fields to the `deck_analysis` table:

```sql
ALTER TABLE deck_analysis 
ADD COLUMN presentation_title VARCHAR(500),
ADD COLUMN presentation_author VARCHAR(500);
```

Update the DeckAnalysis entity:
```java
@Column(name = "presentation_title")
private String presentationTitle;

@Column(name = "presentation_author")
private String presentationAuthor;
```

### Phase 2: AI Prompt Enhancement (1 hour)

Update the DeckAnalysisService prompt to extract title and author:

#### Updated System Prompt
```
You are an expert presentation analyst. Analyze the provided PowerPoint presentation data
and extract key metadata and insights.

Focus on understanding:
1. The actual presentation title (usually the largest/main text on the first slide)
2. The author or company name (often found on the first slide or in speaker notes)
3. The main message and story flow across all slides
4. The intended communication goals and objectives
5. Key themes and topics that recur throughout the presentation
6. The likely target audience based on content and language
7. The overall tone (professional, casual, educational, persuasive, etc.)

For the presentation title:
- Look for the main heading on the first slide
- This is typically the largest text element
- Avoid using filenames or technical identifiers
- Create a clean, professional title suitable for display

For the author:
- Look for author name, company name, or "Presented by" text on the first slide
- Check speaker notes for author information
- If multiple authors, list the primary one
- Format as "By [Name]" or "By [Company Name]"
```

#### Updated Response Format
```json
{
  "presentationTitle": "string - The actual presentation title from the first slide",
  "presentationAuthor": "string - Author name or company, formatted as 'By [Name]'",
  "overallStory": "string - 2-3 paragraph summary of the presentation's story arc",
  "communicationIntent": "string - The primary communication goals",
  "keyThemes": ["theme1", "theme2", "theme3"],
  "targetAudience": "string - Description of the intended audience (max 400 chars)",
  "tone": "string - The overall tone (max 400 chars)"
}
```

### Phase 3: Service Code Updates (1 hour)

#### DeckAnalysisService.java

1. Update `generateDeckAnalysis` method with new prompt
2. Update `parseDeckAnalysis` to handle new fields:

```java
private DeckAnalysis parseDeckAnalysis(String analysisResult, Presentation presentation) {
    // ... existing code ...
    
    // Add new field extraction
    analysis.setPresentationTitle((String) analysisMap.get("presentationTitle"));
    analysis.setPresentationAuthor((String) analysisMap.get("presentationAuthor"));
    
    // ... rest of existing code ...
}
```

### Phase 4: Video Composition Integration (30 minutes)

Update ShotstackCompositionService to use extracted values:

```java
private ObjectNode buildTitleTrack(String title) {
    // Remove TODO and hardcoded value
    asset.put("html", "<p data-html-type=\"text\">" + escapeHtml(title) + "</p>");
}

private ObjectNode buildSubtitleTrack(String subtitle) {
    // Remove TODO and hardcoded value
    asset.put("html", "<p data-html-type=\"text\">" + escapeHtml(subtitle) + "</p>");
}

public List<ObjectNode> buildIntroComposition(Presentation presentation, IntroVideo introVideo) {
    // Use extracted title
    String displayTitle = presentation.getDeckAnalysis() != null 
        && presentation.getDeckAnalysis().getPresentationTitle() != null
        ? presentation.getDeckAnalysis().getPresentationTitle()
        : cleanupFilename(presentation.getTitle()); // fallback
    
    tracks.add(buildTitleTrack(displayTitle));
    
    // Use extracted author
    String displayAuthor = presentation.getDeckAnalysis() != null
        && presentation.getDeckAnalysis().getPresentationAuthor() != null
        ? presentation.getDeckAnalysis().getPresentationAuthor()
        : "A Professional Presentation"; // fallback
    
    tracks.add(buildSubtitleTrack(displayAuthor));
}
```

### Phase 5: Fallback Logic (30 minutes)

Implement smart fallbacks for when deck analysis is not available:

```java
private String cleanupFilename(String filename) {
    // "05052025-tembi-consulting-proposal" -> "Consulting Proposal for Tembi"
    // Remove date prefix
    String cleaned = filename.replaceFirst("^\\d{8}-", "");
    // Replace hyphens with spaces and capitalize
    cleaned = Arrays.stream(cleaned.split("-"))
        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
        .collect(Collectors.joining(" "));
    return cleaned;
}
```

## Testing Strategy

**Manual Testing Only** (POC approach)
- Upload presentations with clear titles and authors on first slide
- Upload presentations without clear metadata to test fallbacks
- Verify deck analysis extracts correct title and author
- Verify video generation uses extracted values in intro section
- Test with various presentation formats and styles

## Migration Strategy

For existing presentations:
1. Add new columns with NULL allowed initially
2. Run a batch job to re-analyze existing presentations
3. Update video composition to handle NULL values gracefully

## Risk Assessment

### Low Risk
- Database changes are additive (no breaking changes)
- Fallback logic ensures system continues working
- AI prompt changes are backward compatible

### Mitigations
- Feature flag for new extraction logic
- Comprehensive fallback chain
- Ability to manually override title/author if needed

## Timeline

- **Day 1**: Database updates and entity changes (30 min)
- **Day 1**: AI prompt enhancement and testing (1 hour)
- **Day 1**: Service code updates (1 hour)
- **Day 1**: Video composition integration (30 min)
- **Day 1**: Testing and refinement (1 hour)

**Total: ~4 hours**

## Success Criteria

1. Deck analysis extracts meaningful presentation titles from first slide
2. Author/company information is correctly identified
3. Video compositions use extracted values instead of hardcoded ones
4. Fallback logic handles edge cases gracefully
5. Existing functionality remains unaffected

## Next Steps

1. Review and approve this plan
2. Create database migration script
3. Implement prompt changes in DeckAnalysisService
4. Update entities and repositories
5. Integrate with video composition
6. Test with various presentation formats