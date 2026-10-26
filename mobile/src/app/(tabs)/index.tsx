import { useCallback, useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { apiFetch } from '@/lib/api';
import { Brand } from '@/constants/brand';

type StatusResponse = {
  online: boolean;
  version: string;
  players: { online: number; max: number; list: string[] };
  discord: { membersCount: number; onlineCount: number };
};

export default function StatusScreen() {
  const [status, setStatus] = useState<StatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<StatusResponse>('/status');
      setStatus(data);
    } catch (err) {
      console.warn('Failed to load server status:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, 30000); // matches the website's live-polling feel
    return () => clearInterval(interval);
  }, [load]);

  const onRefresh = () => { setRefreshing(true); load(); };

  if (loading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator color={Brand.gold} size="large" />
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={Brand.gold} />}>
      <View style={styles.card}>
        <View style={styles.statusRow}>
          <View style={[styles.dot, { backgroundColor: status?.online ? '#4ade80' : '#f87171' }]} />
          <Text style={styles.statusText}>{status?.online ? 'Server online' : 'Server offline'}</Text>
        </View>
        {status?.version ? <Text style={styles.version}>{status.version}</Text> : null}
      </View>

      <View style={styles.card}>
        <View style={styles.statRow}>
          <Ionicons name="people-outline" size={20} color={Brand.gold} />
          <Text style={styles.statLabel}>Spelers online</Text>
          <Text style={styles.statValue}>{status?.players.online ?? 0} / {status?.players.max ?? '?'}</Text>
        </View>
        {status?.players.list && status.players.list.length > 0 && (
          <Text style={styles.playerList}>{status.players.list.join(', ')}</Text>
        )}
      </View>

      <View style={styles.card}>
        <View style={styles.statRow}>
          <Ionicons name="logo-discord" size={20} color={Brand.gold} />
          <Text style={styles.statLabel}>Discord</Text>
          <Text style={styles.statValue}>
            {status?.discord.onlineCount ?? 0} online / {status?.discord.membersCount ?? 0} leden
          </Text>
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Brand.dark, padding: 16 },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: Brand.dark },
  card: { backgroundColor: Brand.charcoal, borderRadius: 12, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: Brand.bronze },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  statusText: { color: '#fff', fontSize: 18, fontWeight: '600' },
  version: { color: '#aaa', marginTop: 4, fontSize: 12 },
  statRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  statLabel: { color: '#ccc', flex: 1, marginLeft: 4 },
  statValue: { color: Brand.gold, fontWeight: '600' },
  playerList: { color: '#888', marginTop: 8, fontSize: 12 }
});
