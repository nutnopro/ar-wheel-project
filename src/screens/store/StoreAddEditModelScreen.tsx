import React, { useState } from 'react';
import AddEditModelForm from '../components/layouts/FormLayout';
import ModelForm from '../components/forms/ModelForm'; // เรียกใช้ฟอร์มกลาง

export default function StoreAddEditModelScreen() {
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');

  const handleSave = () => {
    // Logic ร้านค้า: บันทึกลง Database ของตัวเอง
    console.log('Store Saving:', { name, price });
  };

  return (
    <FormLayout title="My Product" onSave={handleSave}>
      {/* เรียกใช้ฟอร์ม โดยไม่ต้องส่ง props ของ Admin */}
      <ModelForm
        name={name}
        onNameChange={setName}
        price={price}
        onPriceChange={setPrice}
      />
    </FormLayout>
  );
}
