# AI Services for Audio and Video Generation

We want to implement the following AI services to prepare the data for audio and video generation.

---

## Step 1: Analyze User Intent

The first step is to analyze the user's intent. We need to understand the story or message they want to transmit to the audience and the concepts they are using. This analysis is performed on two levels:
- The complete presentation deck
- A single slide

---

## Step 2: Create Narrative

Based on the analysis from the previous step and other data we have already parsed, the AI will create a narrative for each slide that bridges to the following slide(s).

---

## Implementation Details

### Step 1: Analysis

#### 1.1 Overall Deck Analysis

We provide an LLM with the following data for the entire deck in a structured JSON format:
- Extracted text
- Speaker notes
- Slide title
- Slide number

**Output**: The LLM uses this data to construct the overall story and communication intent of the deck, which is then stored in a JSON object.

#### 1.2 Single Slide Analysis

We provide a multimodal LLM with the data for a single slide, including:
- The same data as in 1.1 (text, notes, title, number).
- A high-resolution image of the slide.

**Output**: The multimodal LLM analyzes the image and text to extract the following, which is stored in a structured JSON object for that specific slide:
* The general message of the slide in 2-3 sentences.
* The visual concepts used in the slide (e.g., timelines, agenda, team presentation, workflow in x steps, etc.).

##### Guidelines for 'slideConcepts'
These are some examples we can use in the prompt. This list is not exhaustive and should be expanded.

```json
{
  "guidelines": {
    "TIMELINE": "If the slide shows a sequence of events over time, use 'TIMELINE'. The 'details' should include 'startYear', 'endYear', and a list of 'milestones'.",
    "PROCESS_FLOW": "If the slide shows a step-by-step process, use 'PROCESS_FLOW'. The 'details' should include the 'numberOfSteps' and a list of 'steps'.",
    "COMPARISON": "If the slide compares two or more items, use 'COMPARISON'. The 'details' should include the 'itemsBeingCompared' and a list of 'comparisonPoints'.",
    "LIST": "If the slide is primarily a list of points, use 'LIST'. The 'details' should include the 'topicOfList' and a list of 'items'.",
    "DATA_CHART": "If the slide displays a graph or chart, use 'DATA_CHART'. The 'details' should describe the chart type ('bar', 'line', 'pie'), the 'topic', the 'xAxisLabel', and the 'yAxisLabel'.",
    "ARCHITECTURE_DIAGRAM": "If the slide shows a technical system diagram, use 'ARCHITECTURE_DIAGRAM'. The 'details' should list the key 'components' and their 'relationships'."
  }
}
```

All analyzed data will be stored in the database. We will add data boxes to the frontend to display this information.

---

### Step 2: Narrative Generation

The AI takes the data from Step 1, plus other previously parsed text data, as input.

The objective is for the AI to generate a **fluid narrative** for each slide, which also includes instructions for the video avatar rendering. To create a cohesive flow, the AI will consider not only the current slide but also a few slides forward and backward to make relevant references to upcoming content or previously discussed points. This feature should be used sparingly and only when it genuinely enhances the narrative.

The final narrative is returned in **JSON format** and stored in the database. We will also add data boxes to the frontend to display the generated narrative.