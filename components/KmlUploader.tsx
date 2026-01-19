
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

  // Función auxiliar robusta para leer archivos en entornos híbridos (WebView)
  const readFileAsArrayBuffer = (file: File): Promise<ArrayBuffer> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        if (reader.result instanceof ArrayBuffer) {
          resolve(reader.result);
        } else {
          reject(new Error("Fallo al leer archivo como ArrayBuffer"));
        }
      };
      reader.onerror = () => reject(new Error("Error de lectura de archivo"));
      reader.readAsArrayBuffer(file);
    });
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setIsParsing(true);
    try {
      // 1. Lectura binaria segura
      const buffer = await readFileAsArrayBuffer(file);
      const view = new Uint8Array(buffer);
      
      // 2. Detección de KMZ (ZIP) por firma hexadecimal (PK..)
      // Android a veces pierde la extensión .kmz en los Content Providers
      const isZip = view.length > 4 && 
                    view[0] === 0x50 && view[1] === 0x4B && 
                    view[2] === 0x03 && view[3] === 0x04;

      let kmlText = "";

      if (isZip) {
        try {
          const zip = await JSZip.loadAsync(buffer);
          // Buscar archivo .kml ignorando mayúsculas/minúsculas
          const kmlFileName = Object.keys(zip.files).find(filename => filename.toLowerCase().endsWith('.kml'));
          
          if (kmlFileName) {
            kmlText = await zip.file(kmlFileName)!.async("string");
          } else {
            throw new Error("El archivo KMZ no contiene un .kml válido.");
          }
        } catch (e) {
          console.error("Error descomprimiendo KMZ", e);
          throw new Error("Archivo KMZ corrupto o ilegible.");
        }
      } else {
        // Decodificar KML plano
        const decoder = new TextDecoder("utf-8");
        kmlText = decoder.decode(buffer);
      }

      // 3. Parsing XML Robusto
      const parser = new DOMParser();
      const kml = parser.parseFromString(kmlText, 'text/xml');
      
      // Verificación de errores de XML
      const parserError = kml.getElementsByTagName("parsererror");
      if (parserError.length > 0) {
        console.error("XML Parser Error:", parserError[0].textContent);
        throw new Error("El contenido del archivo no es un XML válido.");
      }

      // Búsqueda agnóstica de Namespaces
      const allElements = kml.getElementsByTagName("*");
      const placemarks = Array.from(allElements).filter(el => 
        el.localName === 'Placemark' || el.nodeName === 'Placemark'
      );
      
      const parcelas: Parcela[] = [];
      
      for (let i = 0; i < placemarks.length; i++) {
        const pm = placemarks[i];
        const children = pm.getElementsByTagName("*");
        
        let name = `Recinto ${i + 1}`;
        let coordsContent = "";

        for (let j = 0; j < children.length; j++) {
            const child = children[j];
            if (child.localName === 'name') name = child.textContent || name;
            if (child.localName === 'coordinates') coordsContent = child.textContent?.trim() || "";
        }
        
        if (coordsContent) {
          const normalizedCoords = coordsContent.replace(/\s+/g, ' ').trim();
          const points = normalizedCoords.split(' ');
          
          if (points.length > 0) {
            const firstPoint = points[0].split(',');
            if (firstPoint.length >= 2) {
                const lng = parseFloat(firstPoint[0]);
                const lat = parseFloat(firstPoint[1]);

                if (!isNaN(lat) && !isNaN(lng)) {
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
      }

      // Fallback agresivo para coordenadas crudas
      if (parcelas.length === 0) {
        console.warn("No se encontraron Placemarks, intentando búsqueda cruda de coordenadas...");
        const rawCoords = Array.from(allElements).filter(el => el.localName === 'coordinates');
        
        rawCoords.forEach((el, idx) => {
            const txt = el.textContent?.trim() || "";
            const parts = txt.split(/\s+/)[0].split(',');
            if (parts.length >= 2) {
                const lng = parseFloat(parts[0]);
                const lat = parseFloat(parts[1]);
                if(!isNaN(lng) && !isNaN(lat)) {
                    parcelas.push({
                        id: `fallback-${idx}`,
                        name: `Geometría Detectada ${idx+1}`,
                        lat, lng, area: 1.0, status: 'pending'
                    });
                }
            }
        });
      }

      if (parcelas.length === 0) {
        console.warn("XML Content Preview:", kmlText.substring(0, 500));
        showNativeToast("No se encontraron geometrías válidas en el archivo.");
      } else {
        const newInspection: Inspection = {
          id: `ins-${Date.now()}`,
          title: file.name.replace(/\.(kml|kmz|xml)$/i, ''),
          description: `Importación automática de ${parcelas.length} recintos.`,
          date: new Date().toISOString().split('T')[0],
          status: 'planned',
          parcelas
        };
        onDataParsed(newInspection);
        showNativeToast(`Importados ${parcelas.length} recintos correctamente.`);
      }
    } catch (error: any) {
      console.error(error);
      showNativeToast(error.message || "Error al procesar el archivo.");
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
        // Accept ampliado para máxima compatibilidad con Android File Picker
        accept=".kml,.kmz,.xml,application/vnd.google-earth.kml+xml,application/vnd.google-earth.kmz,application/xml,text/xml,application/zip,application/x-zip-compressed,multipart/x-zip"
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
        {isParsing ? 'Analizando archivo...' : 'Importar Archivo KML / KMZ'}
      </button>
    </div>
  );
};
