import React, { useRef, useState } from 'react';
import { Loader2, FileUp } from 'lucide-react';
import { Expediente } from '../types';
import { showNativeToast } from '../services/bridge';

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
      // Simulamos procesamiento de KML/KMZ para la UI
      await new Promise(r => setTimeout(r, 1500));
      
      const newExp: Expediente = {
        id: `exp-${Date.now()}`,
        titular: file.name.split('.')[0],
        campana: 2024,
        fechaImportacion: new Date().toLocaleDateString(),
        descripcion: "Importación desde dispositivo",
        status: 'en_curso',
        parcelas: Array(357).fill(null).map((_, i) => ({
          id: `p-${i}`,
          referencia: `Parcela ${i}`,
          uso: 'TA',
          lat: 40, lng: -3, area: 1.5, status: 'pendiente'
        }))
      };

      onDataParsed(newExp);
      showNativeToast(`Importado: ${file.name}`);
    } catch (e: any) {
      showNativeToast("Error al procesar el archivo");
    } finally {
      setIsParsing(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div className="w-full">
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
        className="w-full h-48 dashed-border bg-white/[0.02] flex flex-col items-center justify-center hover:bg-white/[0.05] active:scale-[0.98] transition-all"
      >
        {isParsing ? (
          <Loader2 className="animate-spin text-[#5c60f5] mb-4" size={40} />
        ) : (
          <div className="mb-4 text-[#5c60f5] bg-[#5c60f5]/10 p-4 rounded-xl">
            <FileUp size={36} />
          </div>
        )}
        <h3 className="text-white font-bold text-lg">Importar KML / KMZ</h3>
        <p className="text-gray-500 text-sm mt-1 font-medium">Toque para seleccionar archivo</p>
      </button>
    </div>
  );
};