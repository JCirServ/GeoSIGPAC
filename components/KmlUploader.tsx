
import React, { useRef } from 'react';
import { FileUp, Loader2 } from 'lucide-react';
import { Parcela, Inspection } from '../types';
import { showNativeToast } from '../services/bridge';
import JSZip from 'jszip';

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
      let kmlText = "";

      // Detectar si es KMZ o KML
      if (file.name.toLowerCase().endsWith('.kmz')) {
        try {
          const zip = await JSZip.loadAsync(file);
          // Buscar cualquier archivo que termine en .kml
          const kmlFileName = Object.keys(zip.files).find(filename => filename.toLowerCase().endsWith('.kml'));
          
          if (kmlFileName) {
            kmlText = await zip.file(kmlFileName)!.async("string");
          } else {
            throw new Error("No se encontró un archivo .kml dentro del KMZ.");
          }
        } catch (e) {
          console.error("Error descomprimiendo KMZ", e);
          showNativeToast("El archivo KMZ no es válido o está corrupto.");
          setIsParsing(false);
          return;
        }
      } else {
        // Es un KML normal
        kmlText = await file.text();
      }

      const parser = new DOMParser();
      const kml = parser.parseFromString(kmlText, 'text/xml');
      
      // Usamos getElementsByTagName en lugar de querySelectorAll para:
      // 1. Evitar problemas con namespaces (kml:Placemark vs Placemark)
      // 2. Obtener una colección viva de nodos
      const placemarks = kml.getElementsByTagName('Placemark');
      
      const parcelas: Parcela[] = [];
      
      // Iteramos sobre la colección HTMLCollection
      for (let i = 0; i < placemarks.length; i++) {
        const pm = placemarks[i];
        
        // Obtener nombre: buscamos la etiqueta name dentro del Placemark
        const nameTags = pm.getElementsByTagName('name');
        const name = nameTags.length > 0 ? nameTags[0].textContent || `Recinto ${i + 1}` : `Recinto ${i + 1}`;
        
        // Obtener coordenadas: buscamos 'coordinates' en cualquier profundidad 
        // (dentro de Point, Polygon, MultiGeometry, etc.)
        const coordsTags = pm.getElementsByTagName('coordinates');
        
        if (coordsTags.length > 0) {
          // Tomamos la primera etiqueta de coordenadas encontrada
          const coordsContent = coordsTags[0].textContent?.trim() || "";
          
          // Separadores comunes en KML: espacio, salto de línea o coma
          // El formato suele ser: lon,lat,alt lon,lat,alt ...
          const points = coordsContent.split(/\s+/);
          
          if (points.length > 0 && points[0].includes(',')) {
            const firstPoint = points[0].split(',');
            const lng = parseFloat(firstPoint[0]);
            const lat = parseFloat(firstPoint[1]);

            if (!isNaN(lat) && !isNaN(lng)) {
               // Cálculo de área simulado basado en la cantidad de puntos (mayor complejidad = mayor área aprox para demo)
               const complexity = points.length; 
               const area = (Math.random() * 2 + (complexity * 0.05)).toFixed(2);

               parcelas.push({
                id: `p-${Date.now()}-${i}`,
                name: name,
                lat: lat,
                lng: lng,
                area: parseFloat(area),
                status: 'pending'
              });
            }
          }
        }
      }

      if (parcelas.length === 0) {
        // Si falló Placemark, intentamos un fallback agresivo buscando coordenadas sueltas
        // Esto es útil para estructuras KML muy extrañas o corruptas
        const allCoords = kml.getElementsByTagName('coordinates');
        if (allCoords.length > 0 && parcelas.length === 0) {
            console.log("Intentando fallback de coordenadas...");
             for (let j = 0; j < allCoords.length; j++) {
                const txt = allCoords[j].textContent?.trim() || "";
                const parts = txt.split(/\s+/)[0].split(',');
                if (parts.length >= 2) {
                    const lng = parseFloat(parts[0]);
                    const lat = parseFloat(parts[1]);
                    if(!isNaN(lng) && !isNaN(lat)) {
                        parcelas.push({
                            id: `fallback-${j}`,
                            name: `Geometría ${j+1}`,
                            lat, lng, area: 1.0, status: 'pending'
                        });
                    }
                }
             }
        }
      }

      if (parcelas.length === 0) {
        console.warn("XML Parseado:", kmlText.substring(0, 200)); // Debug
        showNativeToast("El archivo no contiene geometrías válidas.");
      } else {
        const newInspection: Inspection = {
          id: `ins-${Date.now()}`,
          title: file.name.replace(/\.km[lz]/i, ''),
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
      showNativeToast("Error al procesar el archivo.");
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
        accept=".kml, .kmz" 
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
        {isParsing ? 'Procesando...' : 'Importar Archivo KML / KMZ'}
      </button>
    </div>
  );
};
