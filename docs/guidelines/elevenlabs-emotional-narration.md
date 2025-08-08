# ElevenLabs V3 Emotional Narration Guidelines

## Overview

ElevenLabs V3 voices support emotional control through two primary methods: punctuation/capitalization (easier, more natural) and bracketed audio tags (more explicit control).

## Primary Method: Punctuation & Capitalization

### Punctuation Effects

- **Ellipses (...)** - Add pauses and weight to delivery
- **CAPITALIZATION** - Increases emphasis and intensity
- **Question marks (?)** - Natural inquisitive tone
- **Exclamation marks (!)** - Adds excitement or urgency
- **Commas (,)** - Natural pauses and rhythm
- **Periods (.)** - Clear sentence breaks

### Example

"It was a VERY long day... nobody listens anymore."
"This is AMAZING! We finally... we finally did it!"

## Secondary Method: Audio Tags

### Tag Syntax

Tags are applied at the sentence level without closing tags:
`"This is amazing [excited] and I can't wait to share it!"`

### Available Emotion Tags

#### Primary Emotions

- `[excited]` - Energetic, enthusiastic delivery
- `[sad]` - Somber, melancholic tone
- `[angry]` - Aggressive, frustrated delivery
- `[calm]` - Peaceful, measured speech
- `[curious]` - Inquisitive, wondering tone
- `[sarcastic]` - Ironic, mocking delivery

#### Voice Modulations

- `[whispers]` - Quiet, hushed speech
- `[shouts]` - Loud, projecting voice
- `[sighs]` - Exhaling with emotion
- `[laughs]` - Natural laughter
- `[crying]` - Tearful delivery

#### Special Effects

- `[pause]` - Natural speech pause
- `[gasps]` - Sudden intake of breath
- `[clears throat]` - Throat clearing sound

## Implementation Strategy

### 1. Emotion Mapping for Slides

#### Punctuation-Based Approach (Recommended)

- **Title slides**: Use CAPS for key words, exclamation marks
- **Data/charts**: Use ellipses for thoughtful pauses, question marks for curiosity
- **Problem statements**: Ellipses for weight, sparse punctuation for seriousness
- **Solutions**: Exclamation marks, CAPS for emphasis
- **Conclusions**: Strong punctuation, CAPS for key points

#### Tag-Based Enhancement (Optional)

- **Title slides**: `[confident]`, `[excited]`
- **Data/charts**: `[curious]`
- **Problem statements**: `[concerned]`, `[sighs]`
- **Solutions**: `[excited]`
- **Conclusions**: `[confident]`

### 2. Contextual Application

```json
{
  "narrative": "We discovered an AMAZING breakthrough [excited] that will completely transform our approach!",
  "punctuation_emphasis": "high",
  "emotion_tags": ["excited"],
  "primary_technique": "punctuation"
}
```

### 3. Best Practices

- **Prioritize punctuation**: Use natural punctuation and CAPS before adding tags
- **Start subtle**: Begin with punctuation only, add tags sparingly
- **Match content**: Align emotional delivery with slide context
- **Test voices**: Different V3 voices respond differently to both methods
- **Avoid tag overuse**: Maximum 1-2 tags per paragraph
- **Combine carefully**: Don't mix too many techniques in one sentence

### 4. Technical Considerations

- Minimum text length: 250+ characters for consistent results
- Stability setting: Use "Natural" for balanced emotional response
- Voice selection: Test multiple V3 voices for best emotional range
- Fallback strategy: Have plain text version as backup

### 5. Processing Pipeline

1. Analyze slide content type
2. Apply punctuation-based emotions (primary)
3. Add strategic CAPITALIZATION for emphasis
4. Insert audio tags sparingly where needed (secondary)
5. Validate emotional balance (avoid oversaturation)
6. Generate TTS with V3 voice

## Example Implementation

```python
def add_emotions(narrative: str, slide_type: str) -> str:
    # Primary: Apply punctuation-based emotions
    if slide_type == "title":
        # Emphasize key words with CAPS, add excitement
        narrative = emphasize_keywords(narrative, intensity="high")
        narrative = narrative.replace(".", "!")
    elif slide_type == "problem":
        # Add weight with ellipses
        narrative = narrative.replace(". ", "... ")
    elif slide_type == "data":
        # Add curiosity with questions
        narrative = add_rhetorical_questions(narrative)
    
    # Secondary: Add sparse emotion tags if needed
    emotion_tags = {
        "title": "[excited]",
        "problem": "[concerned]",
        "solution": "[excited]",
        "conclusion": "[confident]"
    }
    
    if slide_type in emotion_tags:
        # Add tag to first sentence only
        narrative = f"{narrative} {emotion_tags[slide_type]}"
    
    return narrative
```

## Real Examples

### Title Slide

```text
"Welcome to our BREAKTHROUGH presentation [excited] where we'll explore the FUTURE of technology!"
```

### Problem Statement

```text
"Sales have been declining... [sighs] and we need to address this NOW."
```

### Data Visualization

```text
"What's driving these numbers? Let's look at the data... it's quite REVEALING!"
```

### Solution

```text
"Here's our solution: We're going to REVOLUTIONIZE the process [excited] with automation!"
```

## Testing Checklist

- [ ] Punctuation creates natural emotional flow
- [ ] CAPITALIZATION adds appropriate emphasis
- [ ] Emotion tags used sparingly (max 1-2 per slide)
- [ ] Emotions match slide content and context
- [ ] Natural speech rhythm maintained
- [ ] No oversaturation of emotional markers
- [ ] Fallback to plain text works
