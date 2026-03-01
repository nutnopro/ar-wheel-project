export const MOCK_USERS = [
  { id: '1', name: 'John Doe', email: 'user@test.com', role: 'User', avatar: 'J' },
  { id: '2', name: 'Admin User', email: 'admin@test.com', role: 'Admin', avatar: 'A' },
  { id: '3', name: 'Visitor 001', email: '-', role: 'Visitor', avatar: 'V' },
  { id: '4', name: 'Sarah Smith', email: 'sarah@test.com', role: 'User', avatar: 'S' },
];

export const MOCK_STORES = [
  { id: '1', name: 'Bangkok Wheels Hub', location: 'Bangkok', phone: '02-123-4567' },
  { id: '2', name: 'Chiang Mai Rims', location: 'Chiang Mai', phone: '053-123-456' },
  { id: '3', name: 'Phuket Auto Max', location: 'Phuket', phone: '076-123-456' },
];

export const MOCK_MODELS = [
  { id: '1', name: 'Vossen HF-5', category: 'Sport', stock: 12, price: '$500' },
  { id: '2', name: 'BBS RI-D', category: 'Luxury', stock: 4, price: '$1200' },
];

export const MOCK_CATEGORIES = [
  { id: '1', name: 'Sport Wheels', count: 15 },
  { id: '2', name: 'Luxury Wheels', count: 8 },
  { id: '3', name: 'Off-Road', count: 5 },
];

export const MOCK_LOGS = [
  { id: '1', action: 'User Login', detail: 'user@test.com logged in', time: '10:00 AM' },
  { id: '2', action: 'Update Stock', detail: 'Admin updated Model Vossen HF-5', time: '09:45 AM' },
  { id: '3', action: 'New Order', detail: 'Order #1234 created', time: '09:30 AM' },
  { id: '4', action: 'System Alert', detail: 'High server load detected', time: '08:00 AM' },
];