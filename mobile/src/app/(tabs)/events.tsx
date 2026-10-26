import { useCallback, useEffect, useState } from 'react';
import { View, Text, FlatList, Pressable, StyleSheet, ActivityIndicator, RefreshControl } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { apiFetch } from '@/lib/api';
import { useAuth } from '@/context/auth-context';
import { Brand } from '@/constants/brand';

type EventItem = { id: string; title: string; description: string; date: string; time: string; timezone: string };
type Registration = { id: string; eventId: string; username: string };

export default function EventsScreen() {
  const { user } = useAuth();
  const [events, setEvents] = useState<EventItem[]>([]);
  const [registrations, setRegistrations] = useState<Registration[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<{ events: EventItem[]; registrations: Registration[] }>('/events');
      setEvents(data.events || []);
      setRegistrations(data.registrations || []);
    } catch (err) {
      console.warn('Failed to load events:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const isRegistered = (eventId: string) =>
    !!user?.mcUsername && registrations.some(r => r.eventId === eventId && r.username === user.mcUsername);

  const toggleRegistration = async (eventId: string) => {
    if (!user?.mcUsername) return;
    setBusyId(eventId);
    try {
      const action = isRegistered(eventId) ? 'unregister' : 'register';
      const data = await apiFetch<{ registrations: Registration[] }>('/events/register', {
        method: 'POST',
        body: JSON.stringify({ eventId, username: user.mcUsername, action })
      });
      setRegistrations(data.registrations || []);
    } catch (err) {
      console.warn('Failed to toggle registration:', err);
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator color={Brand.gold} size="large" />
      </View>
    );
  }

  return (
    <FlatList
      style={styles.container}
      contentContainerStyle={{ padding: 16 }}
      data={events}
      keyExtractor={item => item.id}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} tintColor={Brand.gold} />}
      renderItem={({ item }) => {
        const registered = isRegistered(item.id);
        return (
          <View style={styles.card}>
            <Text style={styles.title}>{item.title}</Text>
            <Text style={styles.meta}>
              <Ionicons name="calendar-outline" size={13} color={Brand.gold} /> {item.date} · {item.time} ({item.timezone})
            </Text>
            <Text style={styles.description}>{item.description}</Text>
            <Pressable
              style={[styles.button, registered && styles.buttonActive]}
              disabled={busyId === item.id || !user?.mcUsername}
              onPress={() => toggleRegistration(item.id)}>
              {busyId === item.id ? (
                <ActivityIndicator color="#fff" size="small" />
              ) : (
                <Text style={styles.buttonText}>{registered ? 'Afmelden' : 'Aanmelden'}</Text>
              )}
            </Pressable>
          </View>
        );
      }}
      ListEmptyComponent={<Text style={styles.empty}>Geen events gepland.</Text>}
    />
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Brand.dark },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: Brand.dark },
  card: { backgroundColor: Brand.charcoal, borderRadius: 12, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: Brand.bronze },
  title: { color: '#fff', fontSize: 17, fontWeight: '600' },
  meta: { color: '#aaa', fontSize: 12, marginTop: 4, marginBottom: 8 },
  description: { color: '#ccc', fontSize: 14, marginBottom: 12 },
  button: { backgroundColor: Brand.copper, borderRadius: 8, padding: 10, alignItems: 'center' },
  buttonActive: { backgroundColor: Brand.bronze },
  buttonText: { color: '#fff', fontWeight: '600' },
  empty: { color: '#888', textAlign: 'center', marginTop: 40 }
});
