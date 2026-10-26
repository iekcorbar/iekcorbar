import { View, Text, Pressable, StyleSheet, Image } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { useAuth } from '@/context/auth-context';
import { Brand } from '@/constants/brand';

export default function ProfileScreen() {
  const { user, logout } = useAuth();

  const onLogout = async () => {
    await logout();
    router.replace('/login');
  };

  if (!user) return null;

  return (
    <View style={styles.container}>
      <View style={styles.avatar}>
        {user.customSkinUrl ? (
          <Image source={{ uri: user.customSkinUrl }} style={styles.avatarImage} />
        ) : (
          <Ionicons name="person" size={40} color={Brand.gold} />
        )}
      </View>

      <Text style={styles.name}>{user.mcUsername || user.email}</Text>
      <Text style={styles.email}>{user.email}</Text>
      <View style={styles.roleBadge}><Text style={styles.roleText}>{user.role}</Text></View>

      <Pressable style={styles.logoutButton} onPress={onLogout}>
        <Ionicons name="log-out-outline" size={18} color="#fff" />
        <Text style={styles.logoutText}>Uitloggen</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Brand.dark, alignItems: 'center', paddingTop: 48, padding: 16 },
  avatar: {
    width: 88, height: 88, borderRadius: 44, backgroundColor: Brand.charcoal,
    alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: Brand.gold, overflow: 'hidden'
  },
  avatarImage: { width: '100%', height: '100%' },
  name: { color: '#fff', fontSize: 22, fontWeight: '700', marginTop: 16 },
  email: { color: '#999', fontSize: 13, marginTop: 4 },
  roleBadge: { backgroundColor: Brand.copper, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 4, marginTop: 10 },
  roleText: { color: '#fff', fontSize: 12, fontWeight: '600', textTransform: 'uppercase' },
  logoutButton: {
    flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: Brand.bronze,
    borderRadius: 8, paddingVertical: 12, paddingHorizontal: 20, marginTop: 32
  },
  logoutText: { color: '#fff', fontWeight: '600' }
});
