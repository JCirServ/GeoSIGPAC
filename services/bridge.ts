import { Project } from '../types';

/**
 * Sends a command to the native Android map to focus on specific coordinates.
 * Maps the internal 'focusNativeMap' call to the requested 'onProjectSelected' native method.
 */
export const focusNativeMap = (lat: number, lng: number) => {
  if (window.Android && window.Android.onProjectSelected) {
    window.Android.onProjectSelected(lat, lng);
  } else {
    console.warn("Bridge not found: Mocking onProjectSelected", { lat, lng });
    // Fallback for browser testing
    console.log(`[DEV] Native Map Focus: ${lat}, ${lng}`);
  }
};

/**
 * Triggers the native Android CameraX activity.
 */
export const openNativeCamera = (projectId: string) => {
  if (window.Android && window.Android.openCamera) {
    window.Android.openCamera(projectId);
  } else {
    console.warn("Bridge not found: Mocking openCamera", projectId);
    setTimeout(() => {
      const mockPhoto = "https://picsum.photos/800/600?random=" + Date.now();
      if (window.onPhotoCaptured) {
        window.onPhotoCaptured(projectId, mockPhoto);
      }
    }, 1500);
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
 * Fetches initial projects from Android native database/storage
 */
export const getNativeProjects = (): Project[] | null => {
  if (window.Android && window.Android.getProjects) {
    try {
      const jsonString = window.Android.getProjects();
      return JSON.parse(jsonString);
    } catch (e) {
      console.error("Failed to parse projects from Android", e);
      return null;
    }
  }
  return null;
};