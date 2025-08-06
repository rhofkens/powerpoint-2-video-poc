import { useAnalysisStore } from '@/store/analysisStore';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface AnalysisDebugPanelProps {
  presentationId: string;
}

export function AnalysisDebugPanel({ presentationId }: AnalysisDebugPanelProps) {
  const { analysisStatuses } = useAnalysisStore();
  
  const currentStatuses = analysisStatuses.filter(s => s.presentationId === presentationId);
  
  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle className="text-sm">Debug: Analysis Store State</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-2 text-xs">
          <div>
            <strong>Total Statuses in Store:</strong> {analysisStatuses.length}
          </div>
          <div>
            <strong>Statuses for this presentation:</strong> {currentStatuses.length}
          </div>
          {currentStatuses.map((status, idx) => (
            <div key={idx} className="p-2 bg-muted rounded">
              <div><strong>Type:</strong> {status.analysisType}</div>
              <div><strong>State:</strong> {status.state}</div>
              <div><strong>Progress:</strong> {status.completedItems}/{status.totalItems}</div>
              <div><strong>Message:</strong> {status.message}</div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}