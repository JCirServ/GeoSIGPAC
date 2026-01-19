
import { useState, useCallback, useEffect } from 'react';
import { Inspection, Parcela } from '../types';

const STORAGE_KEY = 'geosigpac_inspections_v2';

export const useProjectStore = () => {
  const [inspections, setInspections] = useState<Inspection[]>([]);
  const [loading, setLoading] = useState(true);

  // Cargar datos persistidos
  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      setInspections(JSON.parse(saved));
    } else {
      // Datos iniciales demo
      const demo: Inspection[] = [{
        id: 'ins-1',
        title: 'Campaña Olivar 2024',
        description: 'Control de cubiertas vegetales y erosión.',
        date: new Date().toISOString().split('T')[0],
        status: 'in_progress',
        parcelas: [
          { id: 'p1', name: 'Recinto 102', lat: 37.3891, lng: -5.9845, area: 4.5, status: 'pending' },
          { id: 'p2', name: 'Recinto 105', lat: 37.3885, lng: -5.9830, area: 2.1, status: 'verified' }
        ]
      }];
      setInspections(demo);
    }
    setLoading(false);
  }, []);

  // Persistir cambios
  useEffect(() => {
    if (!loading) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(inspections));
    }
  }, [inspections, loading]);

  const addInspection = (inspection: Inspection) => {
    setInspections(prev => [inspection, ...prev]);
  };

  const removeInspection = (id: string) => {
    setInspections(prev => prev.filter(i => i.id !== id));
  };

  const updateInspectionStatus = (id: string, status: Inspection['status']) => {
    setInspections(prev => prev.map(i => i.id === id ? { ...i, status } : i));
  };

  const removeParcela = (inspectionId: string, parcelaId: string) => {
    setInspections(prev => prev.map(i => {
      if (i.id === inspectionId) {
        return { ...i, parcelas: i.parcelas.filter(p => p.id !== parcelaId) };
      }
      return i;
    }));
  };

  const handlePhotoCaptured = useCallback((parcelaId: string, photoUri: string) => {
    setInspections(prev => prev.map(ins => ({
      ...ins,
      parcelas: ins.parcelas.map(p => p.id === parcelaId ? { ...p, imageUrl: photoUri, status: 'verified' } : p)
    })));
  }, []);

  useEffect(() => {
    window.onPhotoCaptured = handlePhotoCaptured;
    return () => { window.onPhotoCaptured = undefined; };
  }, [handlePhotoCaptured]);

  return { inspections, addInspection, removeInspection, removeParcela, updateInspectionStatus, loading };
};
