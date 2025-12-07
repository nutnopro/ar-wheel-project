// App.tsx
// import React from 'react';
// import { StatusBar } from 'react-native';
// import { SafeAreaProvider } from 'react-native-safe-area-context';
// import { NavigationContainer } from '@react-navigation/native';
// import { ThemeProvider } from './src/contexts/ThemeContext';
// import { AuthProvider } from './src/contexts/AuthContext';
// import AppNavigator from './src/navigation/AppNavigator';

// export default function App() {
//   return (
//     <SafeAreaProvider>
//       <ThemeProvider>
//         <AuthProvider>
//           <NavigationContainer>
//             <StatusBar barStyle="light-content" />
//             <AppNavigator />
//           </NavigationContainer>
//         </AuthProvider>
//       </ThemeProvider>
//     </SafeAreaProvider>
//   );
// }





import React, { useCallback } from 'react';
import {
  StatusBar,
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  NativeModules,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

const { ARLauncher } = NativeModules;

const HomeScreen = () => {
  const openAR = useCallback(async () => {
    try {
      if (!ARLauncher || typeof ARLauncher.openARActivity !== 'function') {
        throw new Error('ARLauncher native module not available');
      }
      await ARLauncher.openARActivity();
    } catch (err) {
      console.error('❌ Failed to open AR Activity:', err);
    }
  }, []);

  return (
    <View style={homeStyles.container}>
      <Text style={homeStyles.label}>Demo App Test on S23 Ultra</Text>
      <TouchableOpacity style={homeStyles.button} onPress={openAR}>
        <Text style={homeStyles.buttonText}>Open AR Scene</Text>
      </TouchableOpacity>
    </View>
  );
};

const App = () => {
  return (
    <SafeAreaView style={{ flex: 1 }}>
      <StatusBar barStyle="dark-content" />
      <HomeScreen />
    </SafeAreaView>
  );
};

export default App;

const homeStyles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#2d2d2d',
  },
  label: {
    fontSize: 22,
    fontWeight: 'bold',
    marginBottom: 40,
    color: '#fff',
  },
  button: {
    backgroundColor: '#007AFF',
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 12,
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});
