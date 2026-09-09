import { fireEvent, render } from '@testing-library/react-native';

import { Button } from '@/components/ui/Button';
import { AppThemeProvider } from '@/design-system/theme/AppThemeProvider';

function renderButton(props: Partial<React.ComponentProps<typeof Button>> = {}) {
  const onPress = jest.fn();
  const result = render(
    <AppThemeProvider>
      <Button label="Continuar" onPress={onPress} {...props} />
    </AppThemeProvider>,
  );

  return { ...result, onPress };
}

describe('Button', () => {
  it('exposes a button role and handles a press', () => {
    const { getByRole, onPress } = renderButton();

    fireEvent.press(getByRole('button', { name: 'Continuar' }));

    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('announces and enforces its disabled state', () => {
    const { getByRole, onPress } = renderButton({ disabled: true });
    const button = getByRole('button', { name: 'Continuar', disabled: true });

    fireEvent.press(button);

    expect(onPress).not.toHaveBeenCalled();
  });
});
