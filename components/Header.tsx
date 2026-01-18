import React from 'react';
import { Menu, MapPin } from 'lucide-react';

export const Header: React.FC = () => {
  return (
    <header className="sticky top-0 z-10 bg-primary text-white shadow-md p-4 flex items-center justify-between">
      <div className="flex items-center gap-2">
        <div className="bg-white/20 p-2 rounded-full">
          <MapPin size={20} className="text-white" />
        </div>
        <div>
          <h1 className="text-lg font-bold leading-tight">GeoSIGPAC</h1>
          <p className="text-xs text-white/80">Gestor de Parcelas</p>
        </div>
      </div>
      <button className="p-2 active:bg-white/10 rounded-full transition-colors">
        <Menu size={24} />
      </button>
    </header>
  );
};