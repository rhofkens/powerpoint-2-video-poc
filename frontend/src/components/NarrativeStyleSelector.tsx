import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Briefcase, Smile, Frown } from "lucide-react";

export type NarrativeStyle = 'business' | 'funny' | 'cynical';

interface NarrativeStyleSelectorProps {
  value: NarrativeStyle;
  onChange: (style: NarrativeStyle) => void;
  disabled?: boolean;
}

const styleOptions = [
  {
    value: 'business' as NarrativeStyle,
    label: 'Business',
    description: 'Professional, clear, and focused on key insights',
    icon: Briefcase,
  },
  {
    value: 'funny' as NarrativeStyle,
    label: 'Funny',
    description: 'Engaging, witty, and light-hearted approach',
    icon: Smile,
  },
  {
    value: 'cynical' as NarrativeStyle,
    label: 'Cynical',
    description: 'Sharp, skeptical, and thought-provoking commentary',
    icon: Frown,
  },
];

export function NarrativeStyleSelector({ value, onChange, disabled = false }: NarrativeStyleSelectorProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Narrative Style</CardTitle>
        <CardDescription>
          Choose the tone and style for your AI-generated narratives
        </CardDescription>
      </CardHeader>
      <CardContent>
        <RadioGroup
          value={value}
          onValueChange={(newValue) => onChange(newValue as NarrativeStyle)}
          disabled={disabled}
        >
          <div className="grid gap-4">
            {styleOptions.map((option) => {
              const Icon = option.icon;
              return (
                <div key={option.value} className="flex items-start space-x-3">
                  <RadioGroupItem value={option.value} id={option.value} className="mt-1" />
                  <Label
                    htmlFor={option.value}
                    className="flex-1 cursor-pointer space-y-1"
                  >
                    <div className="flex items-center space-x-2">
                      <Icon className="h-4 w-4" />
                      <span className="font-medium">{option.label}</span>
                    </div>
                    <p className="text-sm text-muted-foreground">
                      {option.description}
                    </p>
                  </Label>
                </div>
              );
            })}
          </div>
        </RadioGroup>
      </CardContent>
    </Card>
  );
}