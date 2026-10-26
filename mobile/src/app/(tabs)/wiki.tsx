import { useEffect, useState } from 'react';
import { View, Text, FlatList, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { apiFetch } from '@/lib/api';
import { Brand } from '@/constants/brand';

type WikiPage = { id: string; slug: string; title: string; category: string };

export default function WikiScreen() {
  const [pages, setPages] = useState<WikiPage[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch<{ pages: WikiPage[] }>('/wiki')
      .then(data => setPages(data.pages || []))
      .catch(err => console.warn('Failed to load wiki:', err))
      .finally(() => setLoading(false));
  }, []);

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
      data={pages}
      keyExtractor={item => item.id}
      renderItem={({ item }) => (
        <Pressable style={styles.row} onPress={() => router.push(`/wiki/${item.id}`)}>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>{item.title}</Text>
            <Text style={styles.category}>{item.category}</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#888" />
        </Pressable>
      )}
      ListEmptyComponent={<Text style={styles.empty}>Geen wiki-pagina's gevonden.</Text>}
    />
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Brand.dark },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: Brand.dark },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: Brand.charcoal
  },
  title: { color: '#fff', fontSize: 16, fontWeight: '500' },
  category: { color: Brand.gold, fontSize: 12, marginTop: 2 },
  empty: { color: '#888', textAlign: 'center', marginTop: 40 }
});
