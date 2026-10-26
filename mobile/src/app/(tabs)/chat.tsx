import { useCallback, useEffect, useState } from 'react';
import { View, Text, FlatList, Pressable, StyleSheet, ActivityIndicator, RefreshControl } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { apiFetch } from '@/lib/api';
import { useAuth } from '@/context/auth-context';
import { Brand } from '@/constants/brand';

type RecentChat = { contact: string; lastMessage: string; unreadCount?: number; timestamp?: string };

export default function ChatListScreen() {
  const { user } = useAuth();
  const [chats, setChats] = useState<RecentChat[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    if (!user?.mcUsername) { setLoading(false); return; }
    try {
      const data = await apiFetch<{ chats: RecentChat[] }>(`/chat?username=${encodeURIComponent(user.mcUsername)}&recent=1`);
      setChats(data.chats || []);
    } catch (err) {
      console.warn('Failed to load recent chats:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [user?.mcUsername]);

  // Refresh whenever this tab regains focus (e.g. coming back from a thread).
  useFocusEffect(useCallback(() => { load(); }, [load]));

  if (!user?.mcUsername) {
    return (
      <View style={styles.centered}>
        <Text style={styles.empty}>Koppel eerst je Minecraft-account om te kunnen chatten.</Text>
      </View>
    );
  }

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
      data={chats}
      keyExtractor={item => item.contact}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} tintColor={Brand.gold} />}
      renderItem={({ item }) => (
        <Pressable style={styles.row} onPress={() => router.push(`/chat/${item.contact}`)}>
          <View style={styles.avatar}>
            <Ionicons name="person" size={18} color={Brand.gold} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.contact}>{item.contact}</Text>
            <Text style={styles.lastMessage} numberOfLines={1}>{item.lastMessage}</Text>
          </View>
          {!!item.unreadCount && (
            <View style={styles.badge}><Text style={styles.badgeText}>{item.unreadCount}</Text></View>
          )}
        </Pressable>
      )}
      ListEmptyComponent={<Text style={styles.empty}>Nog geen gesprekken.</Text>}
    />
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Brand.dark },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: Brand.dark, padding: 24 },
  row: {
    flexDirection: 'row', alignItems: 'center', padding: 14,
    borderBottomWidth: 1, borderBottomColor: Brand.charcoal, gap: 12
  },
  avatar: {
    width: 36, height: 36, borderRadius: 18, backgroundColor: Brand.charcoal,
    alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: Brand.bronze
  },
  contact: { color: '#fff', fontSize: 15, fontWeight: '600' },
  lastMessage: { color: '#999', fontSize: 13, marginTop: 2 },
  badge: { backgroundColor: Brand.copper, borderRadius: 10, minWidth: 20, height: 20, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 5 },
  badgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  empty: { color: '#888', textAlign: 'center', marginTop: 40 }
});
