# Visual Concepts Catalog

This document provides a comprehensive catalog of all visual concepts that the AI can identify in presentation slides, along with the expected details for each concept type.

## Concept Structure

Each identified visual concept includes:
- **type**: The concept identifier (e.g., 'TIMELINE', 'SWOT_ANALYSIS')
- **confidence**: Detection confidence level (HIGH/MEDIUM/LOW)
- **position**: Location on slide
  - vertical: TOP/CENTER/BOTTOM
  - horizontal: LEFT/CENTER/RIGHT
- **prominence**: Visual importance (PRIMARY/SECONDARY/SUPPORTING)
- **details**: Concept-specific attributes (see below)

## Structural Concepts

### TIMELINE
Linear progression of events over time.
```json
{
  "startDate": "2020-01",
  "endDate": "2025-12",
  "orientation": "horizontal|vertical",
  "milestones": [
    {
      "date": "2021-06",
      "title": "Product Launch",
      "description": "Initial market release"
    }
  ]
}
```

### PROCESS_FLOW
Step-by-step process or workflow.
```json
{
  "numberOfSteps": 5,
  "flowDirection": "left-to-right|top-to-bottom|circular",
  "stepNames": ["Research", "Design", "Develop", "Test", "Deploy"],
  "decisionPoints": ["Review Gate", "Quality Check"],
  "loops": ["Iteration Loop"]
}
```

### HIERARCHY
Organizational or taxonomic structure.
```json
{
  "levels": 4,
  "topNode": "CEO",
  "branches": ["Operations", "Finance", "Marketing"],
  "nodeCount": 15,
  "structure": "tree|pyramid|org-chart"
}
```

### MATRIX
Grid-based analysis (2x2 or larger).
```json
{
  "dimensions": "2x2|3x3|custom",
  "xAxisLabel": "Impact",
  "yAxisLabel": "Effort",
  "quadrantLabels": ["Quick Wins", "Major Projects", "Fill Ins", "Thankless Tasks"],
  "items": {
    "quadrant1": ["Item A", "Item B"],
    "quadrant2": ["Item C"]
  }
}
```

### CYCLE
Circular or recurring process.
```json
{
  "numberOfStages": 4,
  "stageNames": ["Plan", "Do", "Check", "Act"],
  "direction": "clockwise|counterclockwise",
  "startingPoint": "Plan",
  "continuous": true
}
```

## Comparison Concepts

### COMPARISON_TABLE
Side-by-side feature or option comparison.
```json
{
  "items": ["Option A", "Option B", "Option C"],
  "criteria": ["Price", "Features", "Support", "Performance"],
  "winner": "Option B",
  "recommendation": "Best value for enterprise users"
}
```

### PROS_CONS
Advantages and disadvantages analysis.
```json
{
  "subject": "Cloud Migration",
  "prosList": ["Cost savings", "Scalability", "Flexibility"],
  "consList": ["Security concerns", "Initial setup complexity"],
  "conclusion": "Benefits outweigh risks for most organizations"
}
```

### BEFORE_AFTER
State transformation visualization.
```json
{
  "initialState": "Manual processes, 40% efficiency",
  "finalState": "Automated workflow, 85% efficiency",
  "changesHighlighted": ["Process automation", "Time reduction", "Error elimination"],
  "timeframe": "6 months"
}
```

### VENN_DIAGRAM
Overlapping sets or concepts.
```json
{
  "sets": ["Marketing", "Sales", "Customer Success"],
  "intersections": {
    "marketing_sales": ["Lead qualification", "Content creation"],
    "all_three": ["Customer journey mapping"]
  },
  "uniqueElements": {
    "marketing": ["Brand management", "PR"],
    "sales": ["Negotiations", "Closing"]
  }
}
```

## Data Visualization

### BAR_CHART
Categorical data comparison.
```json
{
  "categories": ["Q1", "Q2", "Q3", "Q4"],
  "values": [150, 230, 180, 310],
  "unit": "$ millions",
  "orientation": "vertical|horizontal",
  "series": ["2023", "2024"]
}
```

### LINE_CHART
Trend visualization over time.
```json
{
  "timeRange": "2020-2024",
  "metrics": ["Revenue", "Profit"],
  "trendDirection": "upward|downward|stable",
  "keyPoints": ["2021 dip", "2023 recovery"],
  "forecast": "Projected 15% growth"
}
```

### PIE_CHART
Part-to-whole relationships.
```json
{
  "segments": ["Product A", "Product B", "Product C", "Other"],
  "percentages": [35, 28, 22, 15],
  "total": "100%",
  "largestSegment": "Product A"
}
```

### SCATTER_PLOT
Correlation analysis between variables.
```json
{
  "xAxis": "Investment",
  "yAxis": "Return",
  "correlation": "positive|negative|none",
  "clusters": ["High performers", "Average", "Underperformers"],
  "outliers": ["Company X", "Company Y"]
}
```

### GAUGE_CHART
Performance metric visualization.
```json
{
  "metric": "Customer Satisfaction",
  "currentValue": 85,
  "target": 90,
  "unit": "%",
  "zones": {
    "red": "0-60",
    "yellow": "60-80",
    "green": "80-100"
  }
}
```

### HEATMAP
Intensity or density visualization.
```json
{
  "dimensions": "regions x products",
  "scale": "0-100",
  "hotspots": ["Northeast - Product A", "West - Product C"],
  "colorScheme": "red-yellow-green"
}
```

## Business Concepts

### SWOT_ANALYSIS
Strategic analysis framework.
```json
{
  "strengths": ["Market leader", "Strong brand", "Innovation"],
  "weaknesses": ["High costs", "Limited distribution"],
  "opportunities": ["Emerging markets", "New technology"],
  "threats": ["Competition", "Regulation changes"]
}
```

### BUSINESS_MODEL
Business model canvas or framework.
```json
{
  "components": ["Value Proposition", "Customer Segments", "Channels"],
  "keyPartnerships": ["Supplier A", "Tech Partner B"],
  "valueProposition": "One-stop solution for SMBs",
  "revenueStreams": ["Subscriptions", "Services"]
}
```

### ROADMAP
Strategic planning timeline.
```json
{
  "timeHorizon": "2024-2027",
  "phases": ["Foundation", "Growth", "Scale", "Optimize"],
  "milestones": [
    {"phase": "Foundation", "items": ["Platform launch", "Initial customers"]},
    {"phase": "Growth", "items": ["Market expansion", "Feature set 2.0"]}
  ],
  "currentStatus": "Completing Foundation phase"
}
```

### KPI_DASHBOARD
Key performance indicators display.
```json
{
  "metrics": [
    {"name": "Revenue", "value": "$2.5M", "target": "$3M", "trend": "up"},
    {"name": "Customer Retention", "value": "92%", "target": "95%", "trend": "stable"},
    {"name": "NPS Score", "value": 72, "target": 80, "trend": "up"}
  ],
  "period": "Q3 2024"
}
```

### FUNNEL
Conversion or process funnel.
```json
{
  "stages": ["Awareness", "Interest", "Consideration", "Purchase", "Loyalty"],
  "conversionRates": ["100%", "40%", "20%", "5%", "3%"],
  "dropoffPoints": ["Interest to Consideration", "Consideration to Purchase"],
  "optimizationFocus": "Improve consideration stage"
}
```

### CUSTOMER_JOURNEY
User experience mapping.
```json
{
  "touchpoints": ["Website", "Sales Call", "Onboarding", "Support", "Renewal"],
  "emotions": ["Curious", "Excited", "Frustrated", "Satisfied", "Loyal"],
  "painPoints": ["Complex pricing", "Long onboarding"],
  "opportunities": ["Self-service options", "Better documentation"]
}
```

## Informational Concepts

### BULLET_LIST
Simple enumeration of points.
```json
{
  "topic": "Key Benefits",
  "items": ["Cost reduction", "Time savings", "Quality improvement"],
  "numbering": "bullets|numbers|letters",
  "hierarchy": "flat|nested"
}
```

### DEFINITION
Concept or term explanation.
```json
{
  "term": "Digital Transformation",
  "definition": "The integration of digital technology into all areas of business",
  "examples": ["Cloud adoption", "Process automation"],
  "relatedConcepts": ["Innovation", "Change Management"]
}
```

### QUOTE
Featured quotation or testimonial.
```json
{
  "quote": "This solution transformed our operations",
  "author": "Jane Smith, CEO",
  "context": "Customer testimonial",
  "emphasis": "transformation"
}
```

### STATISTICS
Key numbers or metrics highlight.
```json
{
  "metrics": [
    {"label": "Growth Rate", "value": "45%", "comparison": "vs 12% industry avg"},
    {"label": "Cost Savings", "value": "$1.2M", "timeframe": "per year"}
  ],
  "significance": "Exceeding all targets"
}
```

### ICON_GRID
Visual representation using icons.
```json
{
  "icons": ["cloud", "mobile", "security", "analytics"],
  "labels": ["Cloud-first", "Mobile-ready", "Secure", "Data-driven"],
  "grouping": "features|benefits|services",
  "arrangement": "2x2|3x3|linear"
}
```

### INFOGRAPHIC
Data storytelling visualization.
```json
{
  "narrative": "The journey to digital transformation",
  "dataPoints": ["70% digitized", "3x faster", "$5M saved"],
  "visualElements": ["icons", "charts", "timelines"],
  "flow": "top-to-bottom"
}
```

## Relational Concepts

### MIND_MAP
Central concept with radiating branches.
```json
{
  "centralIdea": "Product Strategy",
  "branches": {
    "Features": ["Core", "Advanced", "Future"],
    "Markets": ["Enterprise", "SMB", "Consumer"],
    "Technology": ["AI", "Cloud", "Mobile"]
  },
  "connections": ["Features-Markets", "Technology-Features"]
}
```

### NETWORK_DIAGRAM
Interconnected nodes and relationships.
```json
{
  "nodes": ["Server A", "Database", "API Gateway", "Client Apps"],
  "edges": [
    {"from": "Client Apps", "to": "API Gateway", "type": "HTTPS"},
    {"from": "API Gateway", "to": "Server A", "type": "REST"}
  ],
  "clusters": ["Frontend", "Backend", "Data Layer"],
  "centralNodes": ["API Gateway"]
}
```

### FLOWCHART
Decision tree or logic flow.
```json
{
  "startPoint": "User Request",
  "decisions": [
    {"question": "Authenticated?", "yes": "Process", "no": "Login"},
    {"question": "Valid Data?", "yes": "Save", "no": "Error"}
  ],
  "endpoints": ["Success", "Error", "Retry"],
  "conditions": ["If premium user", "If first time"]
}
```

### DEPENDENCY_MAP
Task or system dependencies.
```json
{
  "items": ["Task A", "Task B", "Task C", "Task D"],
  "dependencies": [
    {"from": "Task A", "to": "Task B"},
    {"from": "Task A", "to": "Task C"},
    {"from": "Task B", "to": "Task D"}
  ],
  "criticalPath": ["Task A", "Task B", "Task D"]
}
```

## Specialized Concepts

### GANTT_CHART
Project timeline visualization.
```json
{
  "tasks": [
    {"name": "Research", "start": "2024-01", "duration": "2 months"},
    {"name": "Development", "start": "2024-03", "duration": "4 months"}
  ],
  "dependencies": ["Research → Development"],
  "milestones": ["MVP Launch", "GA Release"],
  "currentDate": "2024-04"
}
```

### RISK_MATRIX
Risk assessment visualization.
```json
{
  "risks": [
    {"name": "Data Breach", "probability": "Low", "impact": "High"},
    {"name": "Budget Overrun", "probability": "Medium", "impact": "Medium"}
  ],
  "quadrants": {
    "high_high": ["Critical risks requiring immediate action"],
    "high_low": ["Monitor closely"],
    "low_high": ["Prepare contingency plans"],
    "low_low": ["Accept risk"]
  },
  "mitigationStrategies": ["Security audit", "Budget controls"]
}
```

### PRICING_TABLE
Product or service pricing tiers.
```json
{
  "tiers": ["Basic", "Professional", "Enterprise"],
  "features": {
    "Basic": ["5 users", "10GB storage", "Email support"],
    "Professional": ["25 users", "100GB storage", "Priority support"],
    "Enterprise": ["Unlimited users", "Unlimited storage", "Dedicated support"]
  },
  "prices": ["$29/mo", "$99/mo", "Custom"],
  "recommended": "Professional"
}
```

### TEAM_STRUCTURE
Organizational layout visualization.
```json
{
  "roles": ["CEO", "CTO", "VP Sales", "VP Marketing"],
  "reportingLines": [
    {"from": "CTO", "to": "CEO"},
    {"from": "Engineers", "to": "CTO"}
  ],
  "departments": ["Engineering", "Sales", "Marketing", "Operations"],
  "teamSizes": {"Engineering": 45, "Sales": 20}
}
```

### CALL_TO_ACTION
Action prompt for audience.
```json
{
  "action": "Schedule a Demo",
  "urgency": "Limited time offer",
  "benefits": ["See product in action", "Get personalized consultation"],
  "contactInfo": {
    "phone": "1-800-DEMO",
    "email": "demo@company.com",
    "link": "company.com/demo"
  }
}
```

### AGENDA
Meeting or presentation outline.
```json
{
  "topics": [
    {"title": "Introduction", "duration": "5 min"},
    {"title": "Product Overview", "duration": "15 min"},
    {"title": "Demo", "duration": "20 min"},
    {"title": "Q&A", "duration": "10 min"}
  ],
  "currentItem": "Product Overview",
  "totalDuration": "50 minutes"
}
```

### SUMMARY
Key takeaways or conclusions.
```json
{
  "mainPoints": [
    "Market opportunity is $5B",
    "Our solution is 3x faster",
    "ROI achieved in 6 months"
  ],
  "conclusions": "Strong business case for investment",
  "nextSteps": ["Approve budget", "Form project team", "Begin implementation"]
}
```

### QUESTION_SLIDE
Q&A or discussion prompts.
```json
{
  "questions": [
    "How can we improve customer retention?",
    "What are the key challenges?",
    "What resources do we need?"
  ],
  "context": "Strategic planning discussion",
  "expectedResponses": "Open discussion format"
}
```

## Usage Guidelines

1. **Multiple Concepts**: A single slide can contain multiple visual concepts. The AI should identify all present concepts.

2. **Confidence Levels**:
   - HIGH: Clear, well-defined visual with all expected elements
   - MEDIUM: Partial match or some ambiguity
   - LOW: Possible match but significant uncertainty

3. **Prominence**:
   - PRIMARY: The main visual element that dominates the slide
   - SECONDARY: Supporting visuals that complement the primary
   - SUPPORTING: Minor visual elements

4. **Position**: Helps understand slide layout and visual hierarchy

5. **Details**: Should be as specific as possible, extracting actual data values, labels, and relationships from the slide content.