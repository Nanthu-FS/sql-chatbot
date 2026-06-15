import React from 'react';
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

function icon(focused: boolean, active: IoniconName, inactive: IoniconName) {
  return ({ color, size }: { color: string; size: number }) => (
    <Ionicons name={focused ? active : inactive} size={size} color={color} />
  );
}

export default function TabLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#5B8266',
        tabBarInactiveTintColor: '#8A9490',
        tabBarStyle: {
          backgroundColor: '#FFFFFF',
          borderTopColor: '#E3ECE4',
          borderTopWidth: 1,
          paddingBottom: 4,
        },
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Scan',
          tabBarIcon: ({ focused, color, size }) =>
            icon(focused, 'camera', 'camera-outline')({ color, size }),
        }}
      />
      <Tabs.Screen
        name="cabinet"
        options={{
          title: 'Cabinet',
          tabBarIcon: ({ focused, color, size }) =>
            icon(focused, 'medical', 'medical-outline')({ color, size }),
        }}
      />
      <Tabs.Screen
        name="interactions"
        options={{
          title: 'Interactions',
          tabBarIcon: ({ focused, color, size }) =>
            icon(focused, 'shield-checkmark', 'shield-checkmark-outline')({ color, size }),
        }}
      />
    </Tabs>
  );
}
