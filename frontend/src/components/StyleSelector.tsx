import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export type NarrativeStyle = 'business' | 'funny' | 'cynical';

interface StyleSelectorProps {
  selectedStyle: NarrativeStyle | null;
  onStyleSelect: (style: NarrativeStyle) => void;
}

const styles = [
  {
    id: 'business' as const,
    name: 'Business',
    description: 'Professional, serious, corporate tone',
    icon: '💼',
    color: 'text-primary'
  },
  {
    id: 'funny' as const,
    name: 'Funny',
    description: 'Light-hearted, humorous, engaging',
    icon: '😄',
    color: 'text-success'
  },
  {
    id: 'cynical' as const,
    name: 'Cynical',
    description: 'Sharp, witty, critical perspective',
    icon: '😏',
    color: 'text-warning'
  }
];

export function StyleSelector({ selectedStyle, onStyleSelect }: StyleSelectorProps) {
  return (
    <div className="space-y-6">
      <div className="text-center">
        <h2 className="text-2xl font-bold mb-2">Choose Your Narrative Style</h2>
        <p className="text-muted-foreground">
          Select the tone and style for your presentation narrative
        </p>
      </div>
      
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {styles.map((style) => (
          <Card
            key={style.id}
            className={`p-6 cursor-pointer transition-all duration-300 hover:shadow-lg hover:-translate-y-1 ${
              selectedStyle === style.id 
                ? 'ring-2 ring-primary bg-primary/5' 
                : 'hover:bg-accent/20'
            }`}
            onClick={() => onStyleSelect(style.id)}
          >
            <div className="text-center space-y-4">
              <div className="text-4xl">{style.icon}</div>
              <div>
                <h3 className={`text-lg font-semibold ${style.color}`}>
                  {style.name}
                </h3>
                <p className="text-sm text-muted-foreground mt-1">
                  {style.description}
                </p>
              </div>
              {selectedStyle === style.id && (
                <div className="flex justify-center">
                  <div className="w-2 h-2 bg-primary rounded-full animate-pulse" />
                </div>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}