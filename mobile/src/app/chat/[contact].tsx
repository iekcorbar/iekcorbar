import { useCallback, useEffect, useRef, useState } from 'react';
import { View, Text, FlatList, TextInput, Pressable, StyleSheet, ActivityIndicator, KeyboardAvoidingView, Platform } from 'react-native';
import { useLocalSearchParams, Stack, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { apiFetch } from '@/lib/api';
import { useAuth } from '@/context/auth-context';
import { Brand } from '@/constants/brand';

type Message = { id: string; sender: string; receiver: string; message: string; imageUrl: string | null; timestamp: string };

export default function ChatThreadScreen() {
  const { contact } = useLocalSearchParams<{ contact: string }>();
  const { user } = useAuth();
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const listRef = useRef<FlatList>(null);

  const load = useCallback(async () => {
    if (!user?.mcUsername || !contact) return;
    try {
      const data = await apiFetch<{ messages: Message[] }>(
        `/chat?username=${encodeURIComponent(user.mcUsername)}&contact=${encodeURIComponent(contact)}`
      );
      setMessages(data.messages || []);
      // Best-effort — the contact's messages to us are now visible on screen.
      apiFetch('/chat', {
        method: 'POST',
        body: JSON.stringify({ action: 'read', sender: contact, receiver: user.mcUsername })
      }).catch(() => {});
    } catch (err) {
      console.warn('Failed to load messages:', err);
    } finally {
      setLoading(false);
    }
  }, [user?.mcUsername, contact]);

  useFocusEffect(useCallback(() => { load(); }, [load]));

  useEffect(() => {
    const interval = setInterval(load, 5000); // light polling — no websocket backend for chat yet
    return () => clearInterval(interval);
  }, [load]);

  const send = async () => {
    const text = draft.trim();
    if (!text || !user?.mcUsername || !contact || sending) return;
    setSending(true);
    setDraft('');
    try {
      const res = await apiFetch<{ message: Message }>('/chat', {
        method: 'POST',
        body: JSON.stringify({ action: 'send', sender: user.mcUsername, receiver: contact, message: text })
      });
      setMessages(prev => [...prev, res.message]);
      setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 50);
    } catch (err) {
      console.warn('Failed to send message:', err);
      setDraft(text); // give it back so the user doesn't lose it
    } finally {
      setSending(false);
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
    <>
      <Stack.Screen options={{ title: contact }} />
      <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <FlatList
          ref={listRef}
          data={messages}
          keyExtractor={item => item.id}
          contentContainerStyle={{ padding: 12 }}
          onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: false })}
          renderItem={({ item }) => {
            const mine = item.sender.toLowerCase() === (user?.mcUsername || '').toLowerCase();
            return (
              <View style={[styles.bubble, mine ? styles.bubbleMine : styles.bubbleTheirs]}>
                <Text style={styles.bubbleText}>{item.message}</Text>
              </View>
            );
          }}
        />
        <View style={styles.inputRow}>
          <TextInput
            style={styles.input}
            placeholder="Typ een bericht..."
            placeholderTextColor="#888"
            value={draft}
            onChangeText={setDraft}
            onSubmitEditing={send}
            returnKeyType="send"
          />
          <Pressable style={styles.sendButton} onPress={send} disabled={sending}>
            <Ionicons name="send" size={18} color="#fff" />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Brand.dark },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: Brand.dark },
  bubble: { maxWidth: '80%', borderRadius: 12, padding: 10, marginBottom: 8 },
  bubbleMine: { backgroundColor: Brand.copper, alignSelf: 'flex-end' },
  bubbleTheirs: { backgroundColor: Brand.charcoal, alignSelf: 'flex-start', borderWidth: 1, borderColor: Brand.bronze },
  bubbleText: { color: '#fff' },
  inputRow: { flexDirection: 'row', padding: 10, gap: 8, borderTopWidth: 1, borderTopColor: Brand.charcoal },
  input: { flex: 1, backgroundColor: Brand.charcoal, color: '#fff', borderRadius: 20, paddingHorizontal: 16, paddingVertical: 10 },
  sendButton: { backgroundColor: Brand.copper, borderRadius: 20, width: 40, height: 40, alignItems: 'center', justifyContent: 'center' }
});
