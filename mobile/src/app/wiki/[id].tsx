import { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';

import { apiFetch } from '@/lib/api';
import { Brand } from '@/constants/brand';

type WikiPage = { id: string; title: string; category: string; content: string };

// Lightweight subset of the website's src/lib/markdown.js renderer, ported to RN
// primitives (that one returns web DOM elements, which don't work here). Covers
// headers, bold, lists and blockquotes — enough for wiki prose. Images/tables are
// left as a fast-follow if wiki pages start relying on them.
function renderMarkdownLines(content: string) {
  const lines = content.split('\n');
  return lines.map((line, i) => {
    const trimmed = line.trim();
    if (!trimmed) return null;

    if (trimmed.startsWith('#### ')) return <Text key={i} style={styles.h4}>{trimmed.slice(5)}</Text>;
    if (trimmed.startsWith('### ')) return <Text key={i} style={styles.h3}>{trimmed.slice(4)}</Text>;
    if (trimmed.startsWith('## ')) return <Text key={i} style={styles.h2}>{trimmed.slice(3)}</Text>;
    if (trimmed.startsWith('> ')) return <Text key={i} style={styles.quote}>{trimmed.slice(2)}</Text>;
    if (trimmed.startsWith('* ') || trimmed.startsWith('- ')) {
      return <Text key={i} style={styles.listItem}>{'•  '}{renderInline(trimmed.slice(2))}</Text>;
    }
    return <Text key={i} style={styles.paragraph}>{renderInline(trimmed)}</Text>;
  });
}

function renderInline(text: string): string {
  // **bold** markers aren't rendered as actual bold here to keep this simple —
  // stripped rather than shown literally with asterisks.
  return text.replace(/\*\*(.+?)\*\*/g, '$1');
}

export default function WikiDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [page, setPage] = useState<WikiPage | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch<{ pages: WikiPage[] }>('/wiki')
      .then(data => setPage(data.pages.find(p => p.id === id) || null))
      .catch(err => console.warn('Failed to load wiki page:', err))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator color={Brand.gold} size="large" />
      </View>
    );
  }

  if (!page) {
    return (
      <View style={styles.centered}>
        <Text style={styles.paragraph}>Pagina niet gevonden.</Text>
      </View>
    );
  }

  return (
    <>
      <Stack.Screen options={{ title: page.title }} />
      <ScrollView style={styles.container} contentContainerStyle={{ padding: 16 }}>
        <Text style={styles.pageTitle}>{page.title}</Text>
        {renderMarkdownLines(page.content)}
      </ScrollView>
    </>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Brand.dark },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: Brand.dark },
  pageTitle: { color: Brand.gold, fontSize: 24, fontWeight: '700', marginBottom: 12 },
  h2: { color: '#fff', fontSize: 20, fontWeight: '700', marginTop: 16, marginBottom: 6 },
  h3: { color: '#fff', fontSize: 17, fontWeight: '600', marginTop: 12, marginBottom: 4 },
  h4: { color: '#fff', fontSize: 15, fontWeight: '600', marginTop: 10, marginBottom: 4 },
  paragraph: { color: '#ddd', fontSize: 15, lineHeight: 22, marginBottom: 8 },
  listItem: { color: '#ddd', fontSize: 15, lineHeight: 22, marginLeft: 8, marginBottom: 4 },
  quote: { color: '#aaa', fontSize: 15, fontStyle: 'italic', borderLeftWidth: 3, borderLeftColor: Brand.gold, paddingLeft: 10, marginBottom: 8 }
});
