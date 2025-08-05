import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { CheckCircle, XCircle, Loader2, Server } from 'lucide-react';

interface RendererInfo {
  key: string;
  name: string;
  available: boolean;
  metrics?: {
    averageRenderTimeMs: number;
    slidesRendered: number;
    renderingFailures: number;
  };
}

export function RendererStatus() {
  const [renderers, setRenderers] = useState<RendererInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchRendererStatus();
  }, []);

  const fetchRendererStatus = async () => {
    try {
      setLoading(true);
      const response = await fetch('http://localhost:8080/api/renderers/status');
      if (!response.ok) {
        throw new Error('Failed to fetch renderer status');
      }
      const data = await response.json();
      setRenderers(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  const getRendererBadgeVariant = (available: boolean) => {
    return available ? 'default' : 'secondary';
  };

  const getRendererIcon = (available: boolean) => {
    return available ? (
      <CheckCircle className="h-3 w-3 text-green-500" />
    ) : (
      <XCircle className="h-3 w-3 text-gray-400" />
    );
  };

  if (loading) {
    return (
      <Card className="p-4">
        <div className="flex items-center space-x-2">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span className="text-sm text-muted-foreground">Loading renderer status...</span>
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-4 border-red-200 bg-red-50">
        <div className="flex items-center space-x-2 text-red-600">
          <XCircle className="h-4 w-4" />
          <span className="text-sm">Failed to load renderer status: {error}</span>
        </div>
      </Card>
    );
  }

  return (
    <Card className="p-4">
      <div className="space-y-3">
        <div className="flex items-center space-x-2 mb-2">
          <Server className="h-4 w-4 text-muted-foreground" />
          <h3 className="text-sm font-semibold">Available Renderers</h3>
        </div>
        
        {renderers.map((renderer) => (
          <div key={renderer.name} className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              {getRendererIcon(renderer.available)}
              <span className="text-sm font-medium">{renderer.name}</span>
              {renderer.name === 'MSGRAPH' && (
                <Badge variant="outline" className="text-xs">Cloud</Badge>
              )}
            </div>
            
            <Badge variant={getRendererBadgeVariant(renderer.available)}>
              {renderer.available ? 'Available' : 'Unavailable'}
            </Badge>
          </div>
        ))}
        
        {renderers.length === 0 && (
          <p className="text-sm text-muted-foreground">No renderers found</p>
        )}
        
        <div className="pt-2 border-t">
          <p className="text-xs text-muted-foreground">
            Renderers are selected automatically based on availability and priority.
            {renderers.find(r => r.key === 'MSGRAPH' && r.available) && (
              <span className="block mt-1 text-green-600">
                ✓ MS Graph renderer is active for high-quality rendering
              </span>
            )}
          </p>
        </div>
      </div>
    </Card>
  );
}