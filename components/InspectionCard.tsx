import React from 'react';
import { Expediente } from '../types';
import { List, Trash2 } from 'lucide-react';

interface InspectionCardProps {
  expediente: Expediente;
  onDelete: (id: string) => void;
}

export const InspectionCard: React.FC<InspectionCardProps> = ({ expediente, onDelete }) => {
  const totalParcelas = expediente.parcelas.length;
  const progress = 0; // Se puede calcular basado en parcelas finalizadas

  return (
    <div className="neon-card p-6">
      <div className="flex justify-between items-start mb-6">
        <div className="flex items-center gap-4">
          {/* Indicador de estado neón */}
          <div className="w-3.5 h-3.5 rounded-full bg-[#5c60f5] shadow-[0_0_12px_rgba(92,96,245,0.8)]" />
          
          <div className="flex flex-col">
            <h3 className="text-white font-bold text-xl leading-none">
              {expediente.titular} (1)
            </h3>
            <p className="text-[#94a3b8] text-sm font-semibold mt-1.5 uppercase tracking-wide opacity-70">
              {totalParcelas} parcelas • {totalParcelas} recintos
            </p>
          </div>
        </div>

        <div className="flex gap-3">
          <button className="w-12 h-12 flex items-center justify-center rounded-full bg-white/5 text-[#5c60f5] hover:bg-white/10 transition-colors">
            <List size={24} />
          </button>
          <button 
            onClick={() => onDelete(expediente.id)}
            className="w-12 h-12 flex items-center justify-center rounded-full bg-white/5 text-[#94a3b8] hover:text-red-400 hover:bg-red-500/10 transition-colors"
          >
            <Trash2 size={22} />
          </button>
        </div>
      </div>

      <div className="space-y-3">
        {/* Barra de progreso */}
        <div className="h-[2px] w-full bg-white/10 rounded-full overflow-hidden">
          <div 
            className="h-full bg-[#00ff88] shadow-[0_0_8px_rgba(0,255,136,0.5)]"
            style={{ width: `${progress}%` }}
          />
        </div>
        <div className="flex justify-end">
          <span className="text-[12px] font-bold text-[#00ff88] uppercase tracking-widest">
            {progress}% Completado
          </span>
        </div>
      </div>
    </div>
  );
};