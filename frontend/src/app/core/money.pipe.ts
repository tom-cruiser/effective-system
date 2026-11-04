import { Pipe, PipeTransform } from '@angular/core';
import { Money } from './api.models';
import { groupDigits } from './money';

/** {@code {amount: "20000", currency: "BIF"}} -> "20,000 BIF". */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(money: Money): string {
    return `${groupDigits(money.amount)} ${money.currency}`;
  }
}
