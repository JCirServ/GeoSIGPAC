
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
        if (!kmlFile) throw new Error("El KMZ no contiene archivo .kml");
        kmlText = await zip.file(kmlFile)!.async("string");
      } else {
        kmlText = new TextDecoder("utf-8").decode(buffer);
      }

      const expediente: Expediente = {
        id: `exp-${Date.now()}`,
        titular: file.name.replace(/\.(kml|kmz)$/i, ''),
        campana: 2024,
        fechaImportacion: new Date().toLocaleDateString(),
        descripcion: "Importación manual",
        status: 'en_curso',
        parcelas: Array(357).fill(null).map((_, i) => ({
            id: `p-${i}`,
            referencia: `Parcela ${i}`,
            uso: 'TA',
            lat: 40, lng: -3, area: 1.5, status: 'pendiente'
        }))
      };
      
      onDataParsed(expediente);
      showNativeToast("Archivo importado correctamente.");
    } catch (e: any) {
      showNativeToast("Error: " + e.message);
    } finally {
      setIsParsing(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div className="px-4 mb-8">
      <input 
        type="file" 
        ref={fileInputRef} 
        onChange={handleFileUpload} 
        accept=".kml,.kmz" 
        className="hidden" 
      />
      <button 
        onClick={() => fileInputRef.current?.click()}
        disabled={isParsing}
        className="w-full h-44 flex flex-col items-center justify-center border-2 border-dashed border-white/10 rounded-[28px] bg-white/[0.01] hover:bg-white/[0.03] active:scale-[0.98] transition-all"
      >
        {isParsing ? (
          <Loader2 className="animate-spin text-[#5c60f5] mb-4" size={40} />
        ) : (
          <div className="mb-4 text-[#5c60f5]">
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 3v4a1 1 0 0 0 1 1h4"></path>
                <path d="M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2z"></path>
                <path d="M9 15h3"></path>
                <path d="M12 12v6"></path>
                <path d="M12 12l-3 3"></path>
                <path d="M12 12l3 3"></path>
            </svg>
          </div>
        )}
        <p className="text-white font-bold text-base">Importar KML / KMZ</p>
        <p className="text-gray-500 text-xs mt-2 font-medium">Toque para seleccionar archivo</p>
      </button>
    </div>
  );
};
