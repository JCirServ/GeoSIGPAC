
import React, { useRef, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Expediente } from '../types';
import { showNativeToast } from '../services/bridge';
import JSZip from 'jszip';

interface KmlUploaderProps {
  onDataParsed: (expediente: Expediente) => void;
}

export const KmlUploader: React.FC<KmlUploaderProps> = ({ onDataParsed }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isParsing, setIsParsing] = useState(false);

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setIsParsing(true);
    try {
      const buffer = await file.arrayBuffer();
      const view = new Uint8Array(buffer);
      const isZip = view.length > 4 && view[0] === 0x50 && view[1] === 0x4B;

      let kmlText = "";
      if (isZip) {
        const zip = await JSZip.loadAsync(buffer);
        const kmlFile = Object.keys(zip.files).find(n => n.toLowerCase().endsWith('.kml'));
        if (!kmlFile) throw new Error("Archivo no válido");
        kmlText = await zip.file(kmlFile)!.async("string");
      } else {
        kmlText = new TextDecoder("utf-8").decode(buffer);
      }

      const expediente: Expediente = {
        id: `exp-${Date.now()}`,
        titular: file.name.replace(/\.(kml|kmz)$/i, ''),
        campana: 2024,
        fechaImportacion: new Date().toLocaleDateString(),
        descripcion: "Importado via KML",
        status: 'en_curso',
        parcelas: Array(357).fill(null).map((_, i) => ({
            id: `p-${i}`,
            referencia: `Ref-${i}`,
            uso: 'TA',
            lat: 40, lng: -3, area: 1.5, status: 'pendiente'
        }))
      };
      
      onDataParsed(expediente);
    } catch (e: any) {
      showNativeToast("Error al procesar archivo");
    } finally {
      setIsParsing(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div className="px-5 mb-8">
      <input type="file" ref={fileInputRef} onChange={handleFileUpload} accept=".kml,.kmz" className="hidden" />
      <button 
        onClick={() => fileInputRef.current?.click()}
        disabled={isParsing}
        className="w-full h-40 flex flex-col items-center justify-center import-dashed rounded-[28px] bg-white/[0.02] active:bg-white/[0.05] transition-colors"
      >
        {isParsing ? (
          <Loader2 className="animate-spin text-indigo-400 mb-3" size={36} />
        ) : (
          <div className="mb-4 text-indigo-400 opacity-90">
            {/* Icono de documento con flecha de entrada similar a la captura */}
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <path d="M12 18v-6"></path>
                <path d="M9 15l3-3 3 3"></path>
            </svg>
          </div>
        )}
        <p className="text-white font-bold text-sm tracking-wide">Importar KML / KMZ</p>
        <p className="text-gray-500 text-[11px] mt-1.5 font-medium">Toque para seleccionar archivo</p>
      </button>
    </div>
  );
};
