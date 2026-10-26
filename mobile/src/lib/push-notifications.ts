import { Platform } from 'react-native';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import Constants from 'expo-constants';
import { apiFetch } from './api';

// Foreground notifications still show a banner/sound instead of being silently queued.
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false
  })
});

// Requests permission, registers the device with Expo's push service, and tells the
// iekcorbar backend about the resulting token so it can target this user later.
// Call this once after a successful login. Keyed by email, not a numeric id — the
// website never exposes users.id to the client, everything is keyed by email there too.
export async function registerForPushNotificationsAsync(email: string): Promise<string | null> {
  if (!Device.isDevice) {
    console.warn('Push notifications require a physical device (or won\'t deliver on most simulators).');
    return null;
  }

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('default', {
      name: 'Iekcorbar',
      importance: Notifications.AndroidImportance.MAX,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#D4AF37' // sp-gold
    });
  }

  const existing = await Notifications.getPermissionsAsync();
  let finalStatus = existing.status;
  if (finalStatus !== 'granted') {
    const requested = await Notifications.requestPermissionsAsync();
    finalStatus = requested.status;
  }
  if (finalStatus !== 'granted') {
    console.warn('Push notification permission was not granted.');
    return null;
  }

  const projectId = Constants.expoConfig?.extra?.eas?.projectId;
  if (!projectId) {
    // Expected until `eas init` has been run for this project — see mobile/README.md.
    console.warn('No EAS projectId configured yet; skipping push token registration.');
    return null;
  }

  const { data: token } = await Notifications.getExpoPushTokenAsync({ projectId });

  await apiFetch('/push/register', {
    method: 'POST',
    body: JSON.stringify({ email, platform: Platform.OS, token })
  });

  return token;
}

export async function unregisterPushToken(token: string): Promise<void> {
  await apiFetch(`/push/register?token=${encodeURIComponent(token)}`, { method: 'DELETE' });
}
