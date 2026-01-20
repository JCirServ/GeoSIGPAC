
import { Expediente } from '../types';

/**
 * Mueve la cámara del mapa nativo (MapLibre) a las coordenadas indicadas.
 */
export const focusNativeMap = (lat: number, lng: number) => {
  if (window.Android && window.Android.onProjectSelected) {
    window.Android.onProjectSelected(lat, lng);
  } else {
    console.log(`[DEV] Native Map Focus: ${lat}, ${lng}`);
  }
};

/**
 * Abre la cámara nativa (CameraX) vinculada a una parcela específica.
 */
export const openNativeCamera = (parcelaId: string) => {
  if (window.Android && window.Android.openCamera) {
    window.Android.openCamera(parcelaId);
  } else {
    console.warn("[DEV] Bridge not found. Mocking camera.");
  }
};

export const showNativeToast = (message: string) => {
  if (window.Android && window.Android.showToast) {
    window.Android.showToast(message);
  } else {
    console.log(`[Toast]: ${message}`);
  }
};

/**
 * Sincroniza el expediente parseado con la base de datos nativa (Room).
 * Esto permite que el Mapa Nativo funcione Offline con estos datos.
 */
export const syncInspectionWithNative = (expediente: Expediente) => {
  if (window.Android && window.Android.importInspectionData) {
    // Transformamos al formato DTO que espera Kotlin (JsInspection)
    const dto = {
        title: expediente.titular,
        description: expediente.descripcion,
        date: expediente.fechaImportacion,
        parcelas: expediente.parcelas.map(p => ({
            name: p.referencia,
            lat: p.lat,
            lng: p.lng,
            area: p.area,
            // En el futuro aquí iría la geometría completa del KML
            geometry: [] 
        }))
    };
    
    window.Android.importInspectionData(JSON.stringify(dto));
  } else {
    console.log("[DEV] Syncing with native DB:", expediente);
  }
};
