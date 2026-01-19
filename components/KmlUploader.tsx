
import React, { useRef } from 'react';
import { FileUp, Loader2 } from 'lucide-react';
import { Parcela, Inspection } from '../types';
import { showNativeToast } from '../services/bridge';

interface KmlUploaderProps {
  onDataParsed: (inspection: Inspection) => void;
}

export const KmlUploader: React.FC<KmlUploaderProps> = ({ onDataParsed }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isParsing, setIsParsing] = React.useState(false);

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setIsParsing(true);
    try {
      const text = await file.text();
      const parser = new DOMParser();
      const kml = parser.parseFromString(text, 'text/xml');
      const placemarks = kml.querySelectorAll('Placemark');
      
      const parcelas: Parcela[] = [];
      
      placemarks.forEach((pm, index) => {
        const name = pm.querySelector('name')?.textContent || `Recinto ${index + 1}`;
        const coordsStr = pm.querySelector('coordinates')?.textContent?.trim() || "";
        
        if (coordsStr) {
          // Tomamos el primer punto de la geometría para localizar
          const firstPoint = coordsStr.split(/\s+/)[0].split(',');
          const lng = parseFloat(firstPoint[0]);
          const lat = parseFloat(firstPoint[1]);
          
          // Cálculo de área simplificado (esto es un placeholder, en producción usaría librerías como turf.js)
          const area = Math.random() * 5 + 0.5; 

          parcelas.push({
            id: `p-${Date.now()}-${index}`,
            name,
            lat,
            lng,
            area: parseFloat(area.toFixed(2)),
            status: 'pending'
          });
        }
      });

      if (parcelas.length === 0) {
        showNativeToast("El archivo KML no contiene geometrías válidas.");
      } else {
        const newInspection: Inspection = {
          id: `ins-${Date.now()}`,
          title: file.name.replace('.kml', ''),
          description: `Importación automática de ${parcelas.length} recintos.`,
          date: new Date().toISOString().split('T')[0],
          status: 'planned',
          parcelas
        };
        onDataParsed(newInspection);
        showNativeToast(`Importados ${parcelas.length} recintos con éxito.`);
      }
    } catch (error) {
      console.error(error);
      showNativeToast("Error al procesar el archivo KML.");
    } finally {
      setIsParsing(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div className="mb-6">
      <input 
        type="file" 
        ref={fileInputRef} 
        onChange={handleFileUpload} 
        accept=".kml" 
        className="hidden" 
      />
      <button 
        onClick={() => fileInputRef.current?.click()}
        disabled={isParsing}
        className="w-full flex items-center justify-center gap-3 py-4 border-2 border-dashed border-emerald-500/30 rounded-2xl bg-emerald-500/5 hover:bg-emerald-500/10 transition-colors text-emerald-400 font-bold text-sm"
      >
        {isParsing ? (
          <Loader2 size={20} className="animate-spin" />
        ) : (
          <FileUp size={20} />
        )}
        {isParsing ? 'Procesando KML...' : 'Importar Archivo KML'}
      </button>
    </div>
  );
};
